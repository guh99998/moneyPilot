package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class SupportTicketNotFoundException extends RuntimeException {
    public SupportTicketNotFoundException(Long id) {
        super("Can't find support ticket " + id);
    }

    public SupportTicketNotFoundException(String reference) {
        super("Can't find support ticket " + reference);
    }
}
