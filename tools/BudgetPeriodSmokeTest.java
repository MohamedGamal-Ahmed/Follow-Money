import com.mohamedgamal.masareef.BudgetPeriod;
import java.time.LocalDate;

public final class BudgetPeriodSmokeTest {
    private static void expect(String cycle, String anchor, int days, String today,
                               String start, String end) {
        LocalDate[] actual = BudgetPeriod.bounds(cycle, LocalDate.parse(anchor), days, LocalDate.parse(today));
        if (!actual[0].equals(LocalDate.parse(start)) || !actual[1].equals(LocalDate.parse(end)))
            throw new AssertionError(cycle + ": " + actual[0] + " - " + actual[1]);
    }

    public static void main(String[] args) {
        expect("monthly", "2026-01-31", 1, "2026-02-12", "2026-01-31", "2026-02-28");
        expect("monthly", "2026-01-31", 1, "2026-03-01", "2026-02-28", "2026-03-31");
        expect("weekly", "2026-10-01", 1, "2026-10-09", "2026-10-08", "2026-10-15");
        expect("custom", "2026-10-01", 10, "2026-10-09", "2026-10-01", "2026-10-11");
        expect("once", "2026-10-01", 30, "2026-10-09", "2026-10-01", "2026-10-31");
        System.out.println("Budget period smoke tests passed: 5");
    }
}
