package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.common.MonthDays;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Datas de cobrança de uma compra recorrente que ainda não foram geradas (spec 016, regras 32 e 34).
 * Há uma cobrança por mês, então o que já foi gerado se mede em meses: trocar o dia depois de gerar
 * não cobra o mesmo mês duas vezes, e a cobrança apagada não volta.
 */
public final class RecurringPurchaseSchedule {

    private RecurringPurchaseSchedule() {
    }

    /** Cobranças ainda não geradas, do início (ou do mês seguinte à última gerada) até a data limite, inclusive. */
    public static List<LocalDate> pendingDates(RecurringPurchase purchase, LocalDate upTo) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate limit = purchase.getEndDate() != null && purchase.getEndDate().isBefore(upTo) ? purchase.getEndDate() : upTo;

        YearMonth month = YearMonth.from(purchase.getStartDate());
        if (purchase.getGeneratedUntil() != null) {
            YearMonth afterGenerated = YearMonth.from(purchase.getGeneratedUntil()).plusMonths(1);
            if (afterGenerated.isAfter(month)) {
                month = afterGenerated;
            }
        }

        for (; !MonthDays.clamp(month, purchase.getDayOfMonth()).isAfter(limit); month = month.plusMonths(1)) {
            LocalDate date = MonthDays.clamp(month, purchase.getDayOfMonth());
            if (!date.isBefore(purchase.getStartDate())) {
                dates.add(date);
            }
        }
        return dates;
    }
}
