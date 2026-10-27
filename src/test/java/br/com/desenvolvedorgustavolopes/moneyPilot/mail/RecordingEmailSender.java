package br.com.desenvolvedorgustavolopes.moneyPilot.mail;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.EmailSendException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dublê de teste: guarda as mensagens pedidas e pode simular falha do provedor.
 */
@Component
@Primary
public class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    public void send(EmailMessage message) {
        if (failing) {
            throw new EmailSendException("Não foi possível enviar o e-mail. Tente novamente.");
        }
        sent.add(message);
    }

    public List<EmailMessage> sent() {
        return new ArrayList<>(sent);
    }

    public void failFromNowOn(boolean failing) {
        this.failing = failing;
    }

    public void clear() {
        sent.clear();
        failing = false;
    }
}
