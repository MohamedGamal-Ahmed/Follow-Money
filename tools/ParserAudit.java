import com.mohamedgamal.masareef.ExpenseParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;

/** Local audit: input is id, base64 sender, received epoch, base64 body (TSV).
 * Output contains parsed fields and hashes only, never the original message. */
public final class ParserAudit {
    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            String[] cells = line.split("\t", -1);
            String sender = decode(cells[1]), body = decode(cells[3]);
            long received = Long.parseLong(cells[2]);
            String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((sender + "\n" + body.trim()).getBytes(StandardCharsets.UTF_8)));
            ExpenseParser.Transfer transfer = ExpenseParser.parseTransfer(body);
            ExpenseParser.Parsed parsed = ExpenseParser.parse(sender, body);
            String kind = "expense";
            if (parsed == null) { parsed = ExpenseParser.parseIncome(sender, body); kind = "income"; }
            long amount = transfer != null ? transfer.amountMinor : parsed == null ? 0 : parsed.amountMinor;
            String currency = transfer != null ? "EGP" : parsed == null ? "" : parsed.currency;
            String merchant = transfer != null ? transfer.counterpart : parsed == null ? "" : parsed.merchant;
            String category = transfer != null ? "تحويلات" : parsed == null ? "" : parsed.category;
            if (transfer != null) kind = transfer.incoming ? "income" : "expense";
            if (amount == 0) kind = "ignored";
            System.out.println(String.join("\t", cells[0], hash, Long.toString(ExpenseParser.eventTime(body, received)),
                    kind, Long.toString(amount), currency, parsed == null || parsed.cardLast4 == null ? "" : parsed.cardLast4,
                    encode(merchant), encode(category)));
        }
    }
}
