package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Totais de um dia numa conta: lançamentos comuns separados das transferências (spec 016). */
public interface AccountDayTotals {
    LocalDate getDate();
    /** Despesas que não são transferência. */
    BigDecimal getExpense();
    /** Receitas que não são transferência (estornos, por exemplo). */
    BigDecimal getIncome();
    /** Entradas de transferência: pagamentos de fatura, no caso de um cartão. */
    BigDecimal getTransferIn();
}
