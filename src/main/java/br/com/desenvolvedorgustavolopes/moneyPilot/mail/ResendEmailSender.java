package br.com.desenvolvedorgustavolopes.moneyPilot.mail;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.EmailSendException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("prod")
public class ResendEmailSender implements EmailSender {

    private static final String USER_MESSAGE = "Não foi possível enviar o e-mail. Tente novamente.";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;
    private final String from;

    @Autowired
    public ResendEmailSender(MailProperties properties) {
        // RestClient.Builder não é um bean neste projeto: o cliente é montado aqui, com os timeouts.
        this(properties, RestClient.builder()
                .requestFactory(timeoutFactory())
                .build());
    }

    ResendEmailSender(MailProperties properties, RestClient client) {
        if (isBlank(properties.fromAddress()) || isBlank(properties.resendApiKey())) {
            throw new IllegalStateException(
                    "Mail is not configured: set MAIL_FROM_ADDRESS and RESEND_API_KEY (app.mail.from-address and app.mail.resend-api-key)");
        }
        String name = isBlank(properties.fromName()) ? "Money Pilot" : properties.fromName();
        this.from = name + " <" + properties.fromAddress() + ">";
        this.client = client.mutate()
                .baseUrl(isBlank(properties.baseUrl()) ? "https://api.resend.com" : properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.resendApiKey())
                .build();
    }

    @Override
    public void send(EmailMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(message.to()));
        body.put("subject", message.subject());
        body.put("text", message.text());
        if (message.html() != null && !message.html().isBlank()) {
            body.put("html", message.html());
        }

        try {
            client.post()
                    .uri("/emails")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Email provider rejected the message: status {}", e.getStatusCode().value());
            throw new EmailSendException(USER_MESSAGE, e);
        } catch (RestClientException e) {
            log.error("Email provider unreachable: {}", e.getClass().getSimpleName());
            throw new EmailSendException(USER_MESSAGE, e);
        }
    }

    private static JdkClientHttpRequestFactory timeoutFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
