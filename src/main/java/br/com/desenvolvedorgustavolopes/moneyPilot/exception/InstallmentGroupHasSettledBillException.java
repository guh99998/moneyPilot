package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class InstallmentGroupHasSettledBillException extends RuntimeException {
    public InstallmentGroupHasSettledBillException(Integer installmentNumber, Integer installmentTotal, Long id) {
        super("Installment " + installmentNumber + " of " + installmentTotal + " (bill " + id + ") is already settled; undo its settlement before deleting the group");
    }
}
