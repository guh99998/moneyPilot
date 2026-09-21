package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class TransferSameAccountException extends RuntimeException {
    public TransferSameAccountException() {
        super("Transfer origin and destination accounts must be different");
    }
}
