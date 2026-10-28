package br.com.desenvolvedorgustavolopes.moneyPilot.mail;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingEmailSenderTest {

    @Test
    void send_writesTheContentToTheLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);

        try {
            new LoggingEmailSender().send(new EmailMessage(
                    "ana@example.com", "Confirme", "Clique no link: http://x/y", null, "maria@example.com"));
        } finally {
            logger.detachAppender(logs);
        }

        assertEquals(1, logs.list.size());
        String line = logs.list.get(0).getFormattedMessage();
        assertTrue(line.contains("ana@example.com"));
        assertTrue(line.contains("Confirme"));
        assertTrue(line.contains("maria@example.com"));
        assertTrue(line.contains("Clique no link: http://x/y"));
    }
}
