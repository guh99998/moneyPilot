package br.com.desenvolvedorgustavolopes.moneyPilot.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fora de produção o e-mail não é enviado: o conteúdo aparece no log.
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        log.info("E-mail not sent (non-prod profile). To: {} | Reply-To: {} | Subject: {}\n{}",
                message.to(), message.replyTo(), message.subject(), message.text());
    }
}
