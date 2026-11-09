package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.common.MonthDays;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Ciclo da fatura de um cartão (spec 016), sem banco. O ciclo vai do dia do fechamento até a
 * véspera do fechamento seguinte; a compra no próprio dia do fechamento cai no ciclo novo. Em mês
 * mais curto, fechamento e vencimento valem o último dia.
 */
public record InvoiceCycle(int closingDay, int dueDay) {

    /** start e end são inclusivos; closingDate é o dia em que a fatura fecha (end + 1). */
    public record Cycle(LocalDate start, LocalDate end, LocalDate closingDate, LocalDate dueDate) {
    }

    public Cycle cycleOf(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        LocalDate start = closingIn(month);
        if (date.isBefore(start)) {
            start = closingIn(month.minusMonths(1));
        }
        return cycleStartingAt(start);
    }

    public Cycle next(Cycle cycle) {
        return cycleStartingAt(cycle.closingDate());
    }

    private Cycle cycleStartingAt(LocalDate start) {
        LocalDate closing = closingIn(YearMonth.from(start).plusMonths(1));
        return new Cycle(start, closing.minusDays(1), closing, dueAfter(closing));
    }

    private LocalDate closingIn(YearMonth month) {
        return MonthDays.clamp(month, closingDay);
    }

    private LocalDate dueAfter(LocalDate closing) {
        YearMonth month = YearMonth.from(closing);
        LocalDate due = MonthDays.clamp(month, dueDay);
        if (!due.isAfter(closing)) {
            YearMonth following = month.plusMonths(1);
            due = MonthDays.clamp(following, dueDay);
        }
        return due;
    }
}
