package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pipeline stage 5: adjusts a scored decision with what this user has taught the app on this device. Signals:
 * the user's merchant rules (ignore / category), and per-source message-shape memory of dismissals and confirmations.
 * Shapes are stored only as hashes of source + SmsPreview.shape, never message text. Learning never changes amounts,
 * currency or direction, never creates a transaction from IGNORE, never overrides a hard failure or UNUNDERSTOOD, and
 * lifts only the review caps in ParsingPolicy.LEARNABLE_CAPS. Shadow mode only.
 */
final class LocalLearning {
    /** A user merchant rule, as the legacy settings screen stores it (match text, category, ignore new messages). */
    static final class MerchantRule {
        final String matchText, category;
        final boolean ignored;
        MerchantRule(String matchText, String category, boolean ignored) { this.matchText = matchText; this.category = category; this.ignored = ignored; }
    }

    static final class Learned {
        final String decision, category;
        final Set<String> reasons;
        Learned(String decision, String category, Set<String> reasons) {
            this.decision = decision; this.category = category; this.reasons = Collections.unmodifiableSet(reasons);
        }
    }

    private static final class ShapeMemory {
        int dismissed, confirmed;
        String source, direction;
        boolean conflicting;
    }

    /** One remembered shape as ShadowStore keeps it in learning_rules: a hash and counts, never message text. */
    static final class ShapeRule {
        final String key, source, direction;
        final int dismissed, confirmed;
        final boolean conflicting;
        ShapeRule(String key, String source, int dismissed, int confirmed, String direction, boolean conflicting) {
            this.key = key; this.source = source; this.dismissed = dismissed; this.confirmed = confirmed;
            this.direction = direction; this.conflicting = conflicting;
        }
    }

    private final List<MerchantRule> merchantRules = new ArrayList<>();
    /** Access-ordered: the least recently updated shape is evicted first. */
    private final LinkedHashMap<String, ShapeMemory> shapes = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ShapeMemory> eldest) { return size() > ParsingPolicy.LEARN_MAX_SHAPES; }
    };

    synchronized void setMerchantRules(List<MerchantRule> rules) {
        merchantRules.clear();
        if (rules != null) merchantRules.addAll(rules);
    }

    /** The user removed a message from the ledger as not a transaction (or not theirs). */
    synchronized void recordDismissal(String source, String normalizedText) {
        ShapeMemory memory = memory(source, normalizedText);
        memory.dismissed++;
        if (memory.confirmed > 0) memory.conflicting = true;
    }

    /** The user dismissed this shape at least once and never confirmed it (the missed-messages page reads this). */
    synchronized boolean dismissedShape(String source, String normalizedText) {
        ShapeMemory memory = shapes.get(key(source, normalizedText));
        return memory != null && memory.dismissed > 0 && memory.confirmed == 0;
    }

    /** The user confirmed a reviewed message as a real transaction in this direction. */
    synchronized void recordConfirmation(String source, String normalizedText, String direction) {
        ShapeMemory memory = memory(source, normalizedText);
        if (memory.dismissed > 0 || (memory.direction != null && !memory.direction.equals(direction))) memory.conflicting = true;
        memory.direction = direction;
        memory.confirmed++;
    }

    synchronized int shapeCount() { return shapes.size(); }

    /** Every remembered shape, least recently updated first. */
    synchronized List<ShapeRule> exportShapes() {
        List<ShapeRule> rules = new ArrayList<>();
        for (Map.Entry<String, ShapeMemory> e : shapes.entrySet()) {
            ShapeMemory m = e.getValue();
            rules.add(new ShapeRule(e.getKey(), m.source, m.dismissed, m.confirmed, m.direction, m.conflicting));
        }
        return rules;
    }

    /** Replaces the shape memory with stored rules, in the order given (least recently updated first). */
    synchronized void restoreShapes(List<ShapeRule> rules) {
        shapes.clear();
        for (ShapeRule rule : rules) {
            ShapeMemory m = new ShapeMemory();
            m.source = rule.source; m.dismissed = rule.dismissed; m.confirmed = rule.confirmed;
            m.direction = rule.direction; m.conflicting = rule.conflicting;
            shapes.put(rule.key, m);
        }
    }

    synchronized Learned apply(String source, String normalizedText, ConfidenceScorer.Confidence scored, TransactionExtractor.Extraction x) {
        Set<String> reasons = new LinkedHashSet<>(scored.reasons);
        String decision = scored.decision, category = null;
        boolean transaction = decision.equals("AUTO") || decision.equals("REVIEW");
        if (!transaction) return new Learned(decision, null, reasons);
        // The user's own merchant rules, matched exactly as the legacy path matches them.
        String merchant = x.merchantSurface != null ? x.merchantSurface : x.merchantKey;
        if (merchant != null) for (MerchantRule rule : merchantRules) {
            if (!MerchantClassifier.matchesRule(merchant, rule.matchText)) continue;
            if (rule.ignored) { reasons.add("LEARNED_IGNORED_MERCHANT"); return new Learned("IGNORE", null, reasons); }
            category = rule.category; reasons.add("LEARNED_CATEGORY");
            break;
        }
        ShapeMemory memory = shapes.get(key(source, normalizedText));
        if (memory != null && !memory.conflicting) {
            if (memory.dismissed >= ParsingPolicy.LEARN_DISMISS_MIN && memory.confirmed == 0) {
                reasons.add("LEARNED_DISMISSED_SHAPE");
                return new Learned("IGNORE", category, reasons);
            }
            if (decision.equals("REVIEW") && memory.confirmed >= ParsingPolicy.LEARN_CONFIRM_MIN && memory.dismissed == 0 &&
                    memory.direction != null && memory.direction.equals(x.direction) && onlyLearnableCaps(reasons)) {
                reasons.add("LEARNED_CONFIRMATION");
                return new Learned("AUTO", category, reasons);
            }
        }
        return new Learned(decision, category, reasons);
    }

    /** At least one learnable cap, no other cap, and no hard failure: only then can confirmations lift the REVIEW. */
    private static boolean onlyLearnableCaps(Set<String> reasons) {
        Set<String> learnable = Set.of(ParsingPolicy.LEARNABLE_CAPS);
        boolean any = false;
        for (String cap : ParsingPolicy.REVIEW_CAPS) if (reasons.contains(cap)) { if (!learnable.contains(cap)) return false; any = true; }
        for (String failure : ParsingPolicy.HARD_FAILURES) if (reasons.contains(failure)) return false;
        return any;
    }

    private ShapeMemory memory(String source, String normalizedText) {
        ShapeMemory memory = shapes.computeIfAbsent(key(source, normalizedText), k -> new ShapeMemory());
        memory.source = source;
        return memory;
    }

    /** Hash of source and structural shape: the same template with another amount or date shares a key; no text is kept. */
    static String key(String source, String normalizedText) {
        return CaptureIdentity.hash(String.valueOf(source) + "\n" + SmsPreview.shape(normalizedText));
    }

    @Override public synchronized String toString() {
        return "LocalLearning{merchantRules=" + merchantRules.size() + ", shapes=" + shapes.size() + "}";
    }
}
