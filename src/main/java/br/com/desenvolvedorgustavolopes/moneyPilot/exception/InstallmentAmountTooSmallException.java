package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import java.math.BigDecimal;

public class InstallmentAmountTooSmallException extends RuntimeException {
    public InstallmentAmountTooSmallException(BigDecimal value, Integer installments) {
        super("Total amount must be at least " + value + " for " + installments + " installments");
    }
}
