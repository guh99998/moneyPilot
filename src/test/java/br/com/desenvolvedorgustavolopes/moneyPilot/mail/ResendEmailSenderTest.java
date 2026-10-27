package br.com.desenvolvedorgustavolopes.moneyPilot.mail;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.EmailSendException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailSenderTest {

    private static final String API_KEY = "re_super_secret_key";
    private static final MailProperties PROPERTIES =
            new MailProperties("nao-responda@exemplo.com", "Money Pilot", API_KEY, "https://resend.test");

    private MockRestServiceServer server;
    private ResendEmailSender sender;
    private ListAppender<ILoggingEvent> logs;
    private Logger senderLogger;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new ResendEmailSender(PROPERTIES, builder.build());

        senderLogger = (Logger) LoggerFactory.getLogger(ResendEmailSender.class);
        logs = new ListAppender<>();
        logs.start();
        senderLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        senderLogger.detachAppender(logs);
    }

    @Test
    void send_postsMessageToProviderWithKeyAndDomainSender() {
        server.expect(requestTo("https://resend.test/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("Money Pilot <nao-responda@exemplo.com>"))
                .andExpect(jsonPath("$.to[0]").value("ana@example.com"))
                .andExpect(jsonPath("$.subject").value("Olá"))
                .andExpect(jsonPath("$.text").value("Texto simples"))
                .andExpect(jsonPath("$.html").value("<p>Texto simples</p>"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        sender.send(new EmailMessage("ana@example.com", "Olá", "Texto simples", "<p>Texto simples</p>"));

        server.verify();
    }

    @Test
    void send_providerError_throwsAndNeverLogsTheKey() {
        server.expect(requestTo("https://resend.test/emails"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"invalid to address\"}"));

        EmailSendException ex = assertThrows(EmailSendException.class,
                () -> sender.send(new EmailMessage("invalido", "Assunto", "Texto")));

        assertFalse(ex.getMessage().contains(API_KEY));
        assertFalse(logs.list.isEmpty());
        assertTrue(logs.list.stream().noneMatch(e -> e.getFormattedMessage().contains(API_KEY)));
    }

    @Test
    void send_providerDown_throws() {
        server.expect(requestTo("https://resend.test/emails"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(EmailSendException.class,
                () -> sender.send(new EmailMessage("ana@example.com", "Assunto", "Texto")));
    }

    @Test
    void constructor_withoutKeyOrSender_failsWithClearMessage() {
        RestClient client = RestClient.create();

        IllegalStateException noKey = assertThrows(IllegalStateException.class,
                () -> new ResendEmailSender(new MailProperties("a@b.com", "Money Pilot", "", null), client));
        IllegalStateException noFrom = assertThrows(IllegalStateException.class,
                () -> new ResendEmailSender(new MailProperties(null, "Money Pilot", API_KEY, null), client));

        assertTrue(noKey.getMessage().contains("RESEND_API_KEY"));
        assertTrue(noFrom.getMessage().contains("MAIL_FROM_ADDRESS"));
        assertFalse(noFrom.getMessage().contains(API_KEY));
    }
}
