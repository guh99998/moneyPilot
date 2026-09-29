package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

import java.util.UUID;

public class InstallmentGroupNotFoundException extends RuntimeException {
    public InstallmentGroupNotFoundException(UUID groupId) {
        super("Installment group " + groupId + " not found");
    }
}
