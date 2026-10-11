import com.mohamedgamal.masareef.CsvImport;
import java.util.List;

public final class CsvImportSmokeTest {
    public static void main(String[] args) {
        String own = "Date,Type,Merchant,Amount,Currency,Category,Source,Payment method,Destination,Note\r\n"
                + "\"2026-10-09\",\"expense\",\"Shop, Downtown\",20.50,\"EGP\",\"تسوق\",\"CSV\",\"\",\"\",\"a \"\"quoted\"\" note\"\r\n"
                + "\"2026-10-08\",\"income\",\"Salary\",1000.00,\"EGP\",\"دخل\",\"CSV\",\"\",\"\",\"\"\r\n";
        List<CsvImport.Row> rows = CsvImport.parse(own);
        if (rows.size() != 2 || rows.get(0).amountMinor != 2050 || !rows.get(0).merchant.equals("Shop, Downtown") ||
                !rows.get(0).note.equals("a \"quoted\" note") || !rows.get(1).kind.equals("income"))
            throw new AssertionError("Export CSV import failed");
        List<CsvImport.Row> bank = CsvImport.parse("Date,Description,Amount\n08/10/2026,Cafe,-15.25\n");
        if (bank.get(0).amountMinor != 1525 || !bank.get(0).kind.equals("expense"))
            throw new AssertionError("Bank CSV import failed");
        System.out.println("CSV import smoke tests passed: 2");
    }
}
