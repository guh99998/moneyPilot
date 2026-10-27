package br.com.desenvolvedorgustavolopes.moneyPilot.mail;

public interface EmailSender {

    /**
     * Envia o e-mail de forma síncrona.
     *
     * @throws br.com.desenvolvedorgustavolopes.moneyPilot.exception.EmailSendException se o envio falhar
     */
    void send(EmailMessage message);
}
