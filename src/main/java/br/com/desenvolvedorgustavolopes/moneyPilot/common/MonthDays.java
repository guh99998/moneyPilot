package br.com.desenvolvedorgustavolopes.moneyPilot.common;

import java.time.LocalDate;
import java.time.YearMonth;

/** Dia do mês que, em mês mais curto, vale o último dia (vencimento, fechamento e cobrança recorrente). */
public final class MonthDays {

    private MonthDays() {
    }

    public static LocalDate clamp(YearMonth month, int dayOfMonth) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }
}
