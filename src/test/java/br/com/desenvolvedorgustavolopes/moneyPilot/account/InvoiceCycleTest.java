package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Spec 016: ciclo e vencimento da fatura, sem banco. */
class InvoiceCycleTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static void assertCycle(InvoiceCycle.Cycle cycle, String start, String end, String closing, String due) {
        assertEquals(d(start), cycle.start(), "start");
        assertEquals(d(end), cycle.end(), "end");
        assertEquals(d(closing), cycle.closingDate(), "closingDate");
        assertEquals(d(due), cycle.dueDate(), "dueDate");
    }

    @Test
    void purchaseBeforeClosing_fallsInTheInvoiceThatClosesThisMonth() {
        InvoiceCycle cycle = new InvoiceCycle(10, 17);

        assertCycle(cycle.cycleOf(d("2026-11-05")), "2026-10-10", "2026-11-09", "2026-11-10", "2026-11-17");
    }

    @Test
    void purchaseOnTheClosingDay_fallsInTheNextInvoice() {
        InvoiceCycle cycle = new InvoiceCycle(10, 17);

        assertCycle(cycle.cycleOf(d("2026-11-10")), "2026-11-10", "2026-12-09", "2026-12-10", "2026-12-17");
    }

    @Test
    void purchaseTheDayBeforeClosing_stillFallsInTheClosingInvoice() {
        InvoiceCycle cycle = new InvoiceCycle(10, 17);

        assertCycle(cycle.cycleOf(d("2026-11-09")), "2026-10-10", "2026-11-09", "2026-11-10", "2026-11-17");
    }

    @Test
    void dueDayBeforeClosingDay_dueDateIsInTheFollowingMonth() {
        InvoiceCycle cycle = new InvoiceCycle(25, 5);

        assertCycle(cycle.cycleOf(d("2026-11-10")), "2026-10-25", "2026-11-24", "2026-11-25", "2026-12-05");
    }

    @Test
    void dueDayEqualToClosingDay_dueDateIsInTheFollowingMonth() {
        InvoiceCycle cycle = new InvoiceCycle(10, 10);

        assertCycle(cycle.cycleOf(d("2026-11-05")), "2026-10-10", "2026-11-09", "2026-11-10", "2026-12-10");
    }

    @Test
    void yearTurn_cycleCrossesDecemberToJanuary() {
        InvoiceCycle cycle = new InvoiceCycle(25, 5);

        assertCycle(cycle.cycleOf(d("2026-12-28")), "2026-12-25", "2027-01-24", "2027-01-25", "2027-02-05");
    }

    @Test
    void closingDay31_inShortMonthsUsesTheLastDay() {
        InvoiceCycle cycle = new InvoiceCycle(31, 10);

        assertCycle(cycle.cycleOf(d("2027-02-15")), "2027-01-31", "2027-02-27", "2027-02-28", "2027-03-10");
        assertCycle(cycle.cycleOf(d("2027-02-28")), "2027-02-28", "2027-03-30", "2027-03-31", "2027-04-10");
    }

    @Test
    void dueDay31_inShortMonthsUsesTheLastDay() {
        InvoiceCycle cycle = new InvoiceCycle(10, 31);

        assertCycle(cycle.cycleOf(d("2027-01-20")), "2027-01-10", "2027-02-09", "2027-02-10", "2027-02-28");
    }

    @Test
    void leapYearFebruary_closingDay31_closesOnThe29th() {
        InvoiceCycle cycle = new InvoiceCycle(31, 10);

        assertCycle(cycle.cycleOf(d("2028-02-10")), "2028-01-31", "2028-02-28", "2028-02-29", "2028-03-10");
    }

    @Test
    void next_returnsTheCycleThatStartsOnTheClosingDate() {
        InvoiceCycle cycle = new InvoiceCycle(10, 17);

        InvoiceCycle.Cycle current = cycle.cycleOf(d("2026-11-05"));

        assertCycle(cycle.next(current), "2026-11-10", "2026-12-09", "2026-12-10", "2026-12-17");
    }
}
