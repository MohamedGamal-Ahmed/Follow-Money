package com.mohamedgamal.masareef;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;

import java.util.Locale;

/** Small custom views and lookup tables for the Follow Money visual identity. */
final class UiKit {
    private UiKit() {}

    /** Seven compact day bars used in the home summary card. */
    static final class MiniBars extends View {
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long[] values = new long[0];
        private String[] labels = new String[0];
        private int baseColor, highlightColor, labelColor;

        MiniBars(Context context) {
            super(context);
            text.setTextAlign(Paint.Align.CENTER);
        }

        void setData(long[] values, String[] labels, int baseColor, int highlightColor, int labelColor, Typeface typeface) {
            this.values = values; this.labels = labels;
            this.baseColor = baseColor; this.highlightColor = highlightColor; this.labelColor = labelColor;
            if (typeface != null) text.setTypeface(typeface);
            setContentDescription("مصروفات آخر " + values.length + " أيام");
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (values.length == 0) return;
            float density = getResources().getDisplayMetrics().density;
            float labelHeight = 14 * density;
            text.setTextSize(9 * density);
            long max = 1;
            for (long value : values) max = Math.max(max, value);
            float slot = getWidth() / (float) values.length;
            float barWidth = Math.min(10 * density, slot * 0.6f);
            float chartHeight = getHeight() - labelHeight;
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < values.length; i++) {
                int position = rtl ? values.length - 1 - i : i;
                float center = slot * position + slot / 2f;
                float height = Math.max(4 * density, chartHeight * values[i] / (float) max);
                boolean last = i == values.length - 1;
                bar.setColor(last ? highlightColor : baseColor);
                canvas.drawRoundRect(new RectF(center - barWidth / 2, chartHeight - height, center + barWidth / 2, chartHeight),
                        barWidth / 2, barWidth / 2, bar);
                if (i < labels.length) {
                    text.setColor(last ? highlightColor : labelColor);
                    canvas.drawText(labels[i], center, getHeight() - 2 * density, text);
                }
            }
        }
    }

    /**
     * Columns (months or days) stacked by spending bucket, in calendar order; RTL puts the earliest on the right.
     * A tap reports the column index so the screen can open that month. Exact values live in the text rows below.
     */
    static final class StackedBars extends View {
        interface OnColumn { void selected(int index); }
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long[][] values = new long[0][];
        private String[] labels = new String[0];
        private int[] colors = new int[0];
        private int highlight = -1, labelColor, emptyColor;
        private OnColumn listener;

        StackedBars(Context context) {
            super(context);
            text.setTextAlign(Paint.Align.CENTER);
            setClickable(true);
        }

        /** values[column][segment]; colors[segment]. */
        void setData(long[][] values, String[] labels, int[] colors, int highlight, int labelColor, int emptyColor,
                     Typeface typeface, String description, OnColumn listener) {
            this.values = values; this.labels = labels; this.colors = colors; this.highlight = highlight;
            this.labelColor = labelColor; this.emptyColor = emptyColor; this.listener = listener;
            if (typeface != null) text.setTypeface(typeface);
            setContentDescription(description);
            invalidate();
        }

        private int columnAt(float x) {
            if (values.length == 0) return -1;
            float slot = getWidth() / (float) values.length;
            int position = Math.max(0, Math.min(values.length - 1, (int) (x / slot)));
            return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? values.length - 1 - position : position;
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP && listener != null) {
                int column = columnAt(event.getX());
                if (column >= 0) { listener.selected(column); performClick(); }
            }
            return true;
        }

        @Override public boolean performClick() { return super.performClick(); }

        @Override protected void onDraw(Canvas canvas) {
            if (values.length == 0) return;
            float density = getResources().getDisplayMetrics().density;
            float labelHeight = labels.length > 0 ? 16 * density : 0;
            text.setTextSize((values.length > 16 ? 8 : 10) * density);
            long max = 1;
            for (long[] column : values) { long sum = 0; for (long v : column) sum += v; max = Math.max(max, sum); }
            float chartHeight = getHeight() - labelHeight - 2 * density;
            grid.setColor(emptyColor); grid.setStrokeWidth(density);
            canvas.drawLine(0, chartHeight, getWidth(), chartHeight, grid);
            float slot = getWidth() / (float) values.length;
            float barWidth = Math.max(3 * density, Math.min(22 * density, slot * 0.62f));
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < values.length; i++) {
                int position = rtl ? values.length - 1 - i : i;
                float center = slot * position + slot / 2f;
                float bottom = chartHeight;
                boolean dim = highlight >= 0 && i != highlight;
                for (int s = 0; s < values[i].length; s++) {
                    if (values[i][s] <= 0) continue;
                    float height = chartHeight * values[i][s] / (float) max;
                    int color = colors[s % colors.length];
                    bar.setColor(dim ? (color & 0x00ffffff) | 0x73000000 : color);
                    canvas.drawRect(center - barWidth / 2, bottom - height, center + barWidth / 2, bottom, bar);
                    bottom -= height;
                }
                if (bottom == chartHeight) {
                    bar.setColor(emptyColor);
                    canvas.drawRect(center - barWidth / 2, chartHeight - 2 * density, center + barWidth / 2, chartHeight, bar);
                }
                boolean showLabel = i < labels.length && labels[i] != null && (values.length <= 16 || i % 5 == 0 || i == values.length - 1);
                if (showLabel) {
                    text.setColor(i == highlight ? colors[0] : labelColor);
                    text.setFakeBoldText(i == highlight);
                    canvas.drawText(labels[i], center, getHeight() - 3 * density, text);
                }
            }
        }
    }

    /** Daily money in (up) and out (down) around a center line; moves between own accounts in grey. */
    static final class FlowBars extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long[] in = new long[0], out = new long[0], own = new long[0];
        private int inColor, outColor, ownColor, lineColor;

        FlowBars(Context context) { super(context); }

        void setData(long[] in, long[] out, long[] own, int inColor, int outColor, int ownColor, int lineColor) {
            this.in = in; this.out = out; this.own = own;
            this.inColor = inColor; this.outColor = outColor; this.ownColor = ownColor; this.lineColor = lineColor;
            setContentDescription("الدخل والخرج لكل يوم");
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            int days = in.length;
            if (days == 0) return;
            float density = getResources().getDisplayMetrics().density;
            long max = 1;
            for (int i = 0; i < days; i++) max = Math.max(max, Math.max(in[i] + own[i], out[i] + own[i]));
            float mid = getHeight() / 2f;
            paint.setColor(lineColor);
            canvas.drawRect(0, mid - density / 2, getWidth(), mid + density / 2, paint);
            float slot = getWidth() / (float) days;
            float bar = Math.max(2 * density, Math.min(12 * density, slot * 0.6f));
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < days; i++) {
                int position = rtl ? days - 1 - i : i;
                float left = slot * position + (slot - bar) / 2;
                float up = (mid - 2 * density) * in[i] / (float) max;
                float upOwn = (mid - 2 * density) * own[i] / (float) max;
                float down = (mid - 2 * density) * out[i] / (float) max;
                if (in[i] > 0) { paint.setColor(inColor); canvas.drawRoundRect(new RectF(left, mid - up, left + bar, mid), bar / 3, bar / 3, paint); }
                if (own[i] > 0) {
                    paint.setColor(ownColor);
                    canvas.drawRoundRect(new RectF(left, mid - up - Math.max(2 * density, upOwn), left + bar, mid - up), bar / 3, bar / 3, paint);
                    canvas.drawRoundRect(new RectF(left, mid + down, left + bar, mid + down + Math.max(2 * density, upOwn)), bar / 3, bar / 3, paint);
                }
                if (out[i] > 0) { paint.setColor(outColor); canvas.drawRoundRect(new RectF(left, mid, left + bar, mid + down), bar / 3, bar / 3, paint); }
            }
        }
    }

    /** Circular progress for installments paid out of the total. */
    static final class Ring extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float fraction;

        Ring(Context context, int trackColor, int fillColor) {
            super(context);
            for (Paint paint : new Paint[]{track, fill}) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
            }
            track.setColor(trackColor); fill.setColor(fillColor);
        }

        void setFraction(float value) { fraction = Math.max(0f, Math.min(1f, value)); invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            float stroke = 11 * getResources().getDisplayMetrics().density;
            track.setStrokeWidth(stroke); fill.setStrokeWidth(stroke);
            float size = Math.min(getWidth(), getHeight());
            RectF box = new RectF(stroke / 2 + (getWidth() - size) / 2, stroke / 2 + (getHeight() - size) / 2,
                    (getWidth() + size) / 2 - stroke / 2, (getHeight() + size) / 2 - stroke / 2);
            canvas.drawArc(box, 0, 360, false, track);
            if (fraction > 0) canvas.drawArc(box, -90, 360 * fraction, false, fill);
        }
    }

    /**
     * The statement ring on the home card: a soft track and one arc for the share of the income that has gone out,
     * full when more went out than came in. Colors and stroke are set by the caller, so the ring reads on the
     * brand background in both themes.
     */
    static final class Donut extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float fraction, strokeDp = 14;

        Donut(Context context) {
            super(context);
            for (Paint paint : new Paint[]{track, fill}) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
            }
        }

        void set(float value, int trackColor, int fillColor, float stroke) {
            fraction = Math.max(0f, Math.min(1f, value));
            track.setColor(trackColor); fill.setColor(fillColor); strokeDp = stroke;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float stroke = strokeDp * getResources().getDisplayMetrics().density;
            track.setStrokeWidth(stroke); fill.setStrokeWidth(stroke);
            float size = Math.min(getWidth(), getHeight());
            RectF box = new RectF(stroke / 2 + (getWidth() - size) / 2, stroke / 2 + (getHeight() - size) / 2,
                    (getWidth() + size) / 2 - stroke / 2, (getHeight() + size) / 2 - stroke / 2);
            canvas.drawArc(box, 0, 360, false, track);
            if (fraction > 0) canvas.drawArc(box, -90, Math.max(4f, 360 * fraction), false, fill);
        }
    }

    /** How a known bank's card face looks: display name and gradient colors. */
    static final class CardFace {
        final String name;
        final int start, end, chipTint;
        CardFace(String name, int start, int end, int chipTint) {
            this.name = name; this.start = start; this.end = end; this.chipTint = chipTint;
        }
    }

    static CardFace cardFace(String sourceName) {
        String bank = SourceIdentity.bank(sourceName);
        if ("BANKMISR".equals(bank)) return new CardFace("Banque Misr", 0xff8a2433, 0xff4a0f19, 0);
        if ("HSBC".equals(bank)) return new CardFace("HSBC", 0xff2a2f36, 0xff0e1114, 1);
        if ("NBE".equals(bank)) return new CardFace("NBE", 0xff1f6b4a, 0xff0b3524, 0);
        String key = sourceName == null ? "" : sourceName.toUpperCase(Locale.ROOT);
        if (key.contains("CIB")) return new CardFace("CIB", 0xff1d4f91, 0xff0b2650, 0);
        if (key.contains("QNB")) return new CardFace("QNB", 0xff5b1f6b, 0xff2c0a36, 0);
        if (key.contains("BDC") || key.contains("القاهرة")) return new CardFace("Banque du Caire", 0xff1d5d8f, 0xff0c2c47, 0);
        if (key.contains("INSTAPAY")) return new CardFace("InstaPay", 0xff3c2a7a, 0xff1b1240, 1);
        return new CardFace(sourceName == null || sourceName.isBlank() ? "Follow Money" : sourceName,
                0xff0b3d2e, 0xff06231a, 0);
    }

    /** A provider people commonly pay in installments or subscriptions, with the words that identify its messages. */
    static final class Provider {
        final String name, keywords, kind;
        final int color;
        Provider(String name, String keywords, String kind, int color) {
            this.name = name; this.keywords = keywords; this.kind = kind; this.color = color;
        }
    }

    static final Provider[] PROVIDERS = {
            new Provider("valU", "valu|فاليو", "installment", 0xff0e7c66),
            new Provider("TRU", "tru|ترو", "installment", 0xff1f3a93),
            new Provider("Contact", "contact|كونتكت", "installment", 0xffb0306e),
            new Provider("Souhoola", "souhoola|سهولة", "installment", 0xff7c5cdb),
            new Provider("Aman", "aman|أمان", "installment", 0xff2563a8),
            new Provider("Sympl", "sympl|سمبل", "installment", 0xff111827),
            new Provider("Claude", "claude|anthropic", "subscription", 0xffd97757),
            new Provider("ChatGPT", "chatgpt|openai", "subscription", 0xff10a37f),
            new Provider("Google One", "google one|google storage|google", "subscription", 0xff4285f4),
            new Provider("OneDrive", "onedrive|microsoft", "subscription", 0xff0f6cbd),
            new Provider("Truecaller", "truecaller", "subscription", 0xff0087ff),
            new Provider("Vodafone", "vodafone|فودافون", "bill", 0xffe60000),
            new Provider("Orange", "orange|أورنج", "bill", 0xffff7900),
            new Provider("Etisalat", "etisalat|e&|اتصالات", "bill", 0xff719e19),
            new Provider("WE", "we|المصرية للاتصالات", "bill", 0xff5c2d91),
    };

    static Provider provider(String name) {
        if (name == null) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (Provider provider : PROVIDERS) if (provider.name.toLowerCase(Locale.ROOT).equals(key)) return provider;
        return null;
    }

    private static final int[] MONOGRAM_COLORS = {
            0xff0e7c66, 0xff1f3a93, 0xffb0306e, 0xff7c5cdb, 0xffe8473b, 0xffff6b1a,
            0xff2563a8, 0xff9a7400, 0xff138a5c, 0xff5b3fb0
    };

    /** A stable color for a merchant monogram so the same merchant always looks the same. */
    static int monogramColor(String name) {
        Provider provider = provider(name);
        if (provider != null) return provider.color;
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return MONOGRAM_COLORS[Math.floorMod(key.hashCode(), MONOGRAM_COLORS.length)];
    }

    /** "example@instapay" reads better as "example"; the InstaPay pill says where it came from. */
    static boolean isInstaPay(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("@instapay");
    }

    static String displayName(String name) {
        if (!isInstaPay(name)) return name;
        String local = name.substring(0, name.indexOf('@')).trim();
        return local.isEmpty() ? name : local;
    }

    static String monogram(String name) {
        if (name == null) return "•";
        for (int i = 0; i < name.length(); i++) {
            int point = name.codePointAt(i);
            if (Character.isLetterOrDigit(point)) return new String(Character.toChars(point)).toUpperCase(Locale.ROOT);
        }
        return "•";
    }

    /** Forest plate behind logos that are white on transparency; a white plate would hide them. */
    static final int LOGO_PLATE_FOR_WHITE = 0xff0b3d2e;

    /** Catalog assets drawn entirely in white. Keep in sync with docs/brand-catalog-2026-10-10/README.md. */
    static boolean whiteOnlyLogo(String brandId) {
        return "alexbank".equals(brandId) || "fabmisr".equals(brandId);
    }

    /** Logos drawn on their own solid colour: they fill the whole circle instead of sitting inside a white ring. */
    static boolean fullBleedLogo(String brandId) {
        return brandId != null && java.util.Arrays.asList("bdc", "bookingdotcom", "butlers", "cib", "costa", "etoile", "ezaby",
                "illy", "lcwaikiki", "seoudi", "sharafdg", "tru", "zaher", "paymob", "mahalawy", "pizzaking").contains(brandId);
    }

    /**
     * The one round logo plate used for banks, merchants and the SMS import preview, so a brand looks the
     * same everywhere. {@code border} is 0 for none; light surfaces pass their divider color.
     */
    static ImageView logoBadge(Context context, int logo, String brandId, String description, int sizeDp, int border) {
        float density = context.getResources().getDisplayMetrics().density;
        ImageView icon = new ImageView(context);
        icon.setImageResource(logo);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable plate = new GradientDrawable();
        plate.setColor(whiteOnlyLogo(brandId) ? LOGO_PLATE_FOR_WHITE : 0xffffffff);
        plate.setCornerRadius(sizeDp / 2f * density);
        if (border != 0) plate.setStroke(Math.max(1, Math.round(density)), border);
        icon.setBackground(plate);
        if (fullBleedLogo(brandId)) {
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            icon.setClipToOutline(true);
        } else {
            int inset = Math.round(Math.max(4, sizeDp * 0.14f) * density);
            icon.setPadding(inset, inset, inset, inset);
        }
        icon.setContentDescription(description);
        icon.setImportantForAccessibility(description == null || description.isEmpty()
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return icon;
    }

    /** " •1234" for a known card/account suffix, nothing otherwise (never the text "null"). */
    static String last4Suffix(String last4) {
        return last4 == null || last4.isBlank() ? "" : " •" + last4.trim();
    }
}
