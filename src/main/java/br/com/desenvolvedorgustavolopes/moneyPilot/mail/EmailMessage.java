package br.com.desenvolvedorgustavolopes.moneyPilot.mail;

/**
 * E-mail a enviar. {@code text} é obrigatório (versão legível sem HTML); {@code html} e {@code replyTo} são opcionais.
 */
public record EmailMessage(String to, String subject, String text, String html, String replyTo) {

    public EmailMessage {
        if (to == null || to.isBlank() || subject == null || subject.isBlank() || text == null || text.isBlank()) {
            throw new IllegalArgumentException("Email requires recipient, subject and text");
        }
    }

    public EmailMessage(String to, String subject, String text, String html) {
        this(to, subject, text, html, null);
    }

    public EmailMessage(String to, String subject, String text) {
        this(to, subject, text, null, null);
    }
}
