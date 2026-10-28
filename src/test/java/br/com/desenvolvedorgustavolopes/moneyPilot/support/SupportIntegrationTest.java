package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.RecordingEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupportIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final String MESSAGE = "O saldo da minha conta não bate com o extrato.";
    private static final String SUPPORT_ADDRESS = "suporte@example.test";
    private static final String REFERENCE = "[2-9A-HJKMNP-Z]{6}";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-27T15:00:00Z");
        when(clock.getZone()).thenReturn(SAO_PAULO);
        when(clock.instant()).thenAnswer(invocation -> now);
        emailSender.clear();
    }

    // O RecordingEmailSender é compartilhado com as outras classes que usam o mesmo contexto.
    @AfterEach
    void stopFailing() {
        emailSender.failFromNowOn(false);
    }

    // CA1

    @Test
    void loggedIn_sendsTheTicketToSupportWithTheAccountEmailAsReplyTo() throws Exception {
        String email = newAddress();
        String token = loginAs(email, "Maria Silva");

        ticket(token, body("PROBLEMA", MESSAGE, null), uniqueIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern(REFERENCE)))
                .andExpect(jsonPath("$.replyTo").value(email));

        EmailMessage sent = onlyEmail();
        assertEquals(SUPPORT_ADDRESS, sent.to());
        assertEquals(email, sent.replyTo());
        assertTrue(sent.subject().matches("\\[Suporte " + REFERENCE + "] Problema"), sent.subject());
        assertTrue(sent.text().contains("Categoria: Problema"));
        assertTrue(sent.text().contains(MESSAGE));
        assertTrue(sent.text().contains("E-mail: " + email));
        assertTrue(sent.text().contains("Logada: sim"));
        assertTrue(sent.text().contains("Nome da conta: Maria Silva"));
        // 15:00 UTC é 12:00 em São Paulo
        assertTrue(sent.text().contains("27/10/2026 às 12:00"), sent.text());
    }

    @Test
    void loggedIn_ignoresTheEmailInTheBody() throws Exception {
        String email = newAddress();
        String token = loginAs(email, "Maria Silva");

        ticket(token, body("DUVIDA", MESSAGE, "outra-pessoa@example.com"), uniqueIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyTo").value(email));

        assertEquals(email, onlyEmail().replyTo());
    }

    @Test
    void referenceInTheResponseIsTheOneInTheEmail() throws Exception {
        String token = loginAs(newAddress(), "Maria Silva");

        String response = ticket(token, body("OUTRO", MESSAGE, null), uniqueIp())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(response, "$.reference");

        assertTrue(onlyEmail().subject().contains(reference));
    }

    // CA2

    @Test
    void anonymous_withAnEmail_usesItAsReplyTo() throws Exception {
        ticket(null, body("SUGESTAO", MESSAGE, "  Joao@Example.com "), uniqueIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyTo").value("joao@example.com"));

        EmailMessage sent = onlyEmail();
        assertEquals("joao@example.com", sent.replyTo());
        assertTrue(sent.text().contains("Logada: não"));
        assertFalse(sent.text().contains("Nome da conta"));
        assertTrue(sent.text().contains("Categoria: Sugestão"));
    }

    @Test
    void anonymous_withoutAnEmail_isRefusedOnTheEmailField() throws Exception {
        ticket(null, body("DUVIDA", MESSAGE, null), uniqueIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        ticket(null, body("DUVIDA", MESSAGE, "   "), uniqueIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());

        assertTrue(emailSender.sent().isEmpty());
    }

    @Test
    void anonymous_withAnInvalidEmail_isRefusedOnTheEmailField() throws Exception {
        ticket(null, body("DUVIDA", MESSAGE, "isso-nao-e-email"), uniqueIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());

        assertTrue(emailSender.sent().isEmpty());
    }

    // CA3

    @Test
    void invalidMessage_isRefusedOnTheMessageField_andNothingIsSent() throws Exception {
        String[] invalid = {null, "", "          ", "123456789", "  123456789  ", "a".repeat(2001)};

        for (String message : invalid) {
            ticket(null, body("DUVIDA", message, "ana@example.com"), uniqueIp())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.message").isNotEmpty());
        }

        assertTrue(emailSender.sent().isEmpty());
    }

    @Test
    void messageAtTheLimits_isAccepted() throws Exception {
        ticket(null, body("DUVIDA", "a".repeat(10), "ana@example.com"), uniqueIp()).andExpect(status().isOk());
        ticket(null, body("DUVIDA", "b".repeat(2000), "ana2@example.com"), uniqueIp()).andExpect(status().isOk());

        assertEquals(2, emailSender.sent().size());
    }

    @Test
    void missingOrUnknownCategory_isRefused() throws Exception {
        ticket(null, body(null, MESSAGE, "ana@example.com"), uniqueIp())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.category").isNotEmpty());
        ticket(null, body("COBRANCA", MESSAGE, "ana@example.com"), uniqueIp())
                .andExpect(status().isBadRequest());

        assertTrue(emailSender.sent().isEmpty());
    }

    // CA4

    @Test
    void fourthTicketOfTheAccountInTheHour_isTooManyRequests_andTheWindowReopens() throws Exception {
        String token = loginAs(newAddress(), "Maria Silva");

        for (int i = 0; i < 3; i++) {
            ticket(token, body("DUVIDA", MESSAGE, null), uniqueIp()).andExpect(status().isOk());
        }
        ticket(token, body("DUVIDA", MESSAGE, null), uniqueIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        assertEquals(3, emailSender.sent().size());

        now = now.plus(Duration.ofHours(1));

        ticket(token, body("DUVIDA", MESSAGE, null), uniqueIp()).andExpect(status().isOk());
    }

    @Test
    void fourthTicketOfTheSameEmailWithoutLogin_isTooManyRequests() throws Exception {
        String email = newAddress();

        for (int i = 0; i < 3; i++) {
            ticket(null, body("DUVIDA", MESSAGE, email), uniqueIp()).andExpect(status().isOk());
        }
        ticket(null, body("DUVIDA", MESSAGE, email.toUpperCase()), uniqueIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void eleventhTicketFromTheSameIpWithDifferentEmails_isTooManyRequests() throws Exception {
        String ip = uniqueIp();

        for (int i = 0; i < 10; i++) {
            ticket(null, body("DUVIDA", MESSAGE, newAddress()), ip).andExpect(status().isOk());
        }
        ticket(null, body("DUVIDA", MESSAGE, newAddress()), ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        assertEquals(10, emailSender.sent().size());

        now = now.plus(Duration.ofHours(1));

        ticket(null, body("DUVIDA", MESSAGE, newAddress()), ip).andExpect(status().isOk());
    }

    // CA7 (017): a falha no e-mail não perde o chamado

    @Test
    void sendFailure_savesTheTicket_answersOk_andSpendsTheQuota() throws Exception {
        String email = newAddress();
        String ip = uniqueIp();

        emailSender.failFromNowOn(true);
        for (int i = 0; i < 3; i++) {
            ticket(null, body("PROBLEMA", MESSAGE, email), ip)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern(REFERENCE)));
        }
        ticket(null, body("PROBLEMA", MESSAGE, email), ip).andExpect(status().isTooManyRequests());

        assertEquals(0, emailSender.sent().size());
        assertEquals(3, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM support_tickets WHERE reply_to = ? AND status = 'OPEN' AND email_sent = FALSE",
                Integer.class, email));
    }

    // Bordas

    @Test
    void htmlAccentsAndLineBreaks_arriveAsTyped() throws Exception {
        String message = "<script>alert(1)</script> & ação\nsegunda linha\n\nterceira — com “aspas”";

        ticket(null, body("OUTRO", message, "ana@example.com"), uniqueIp()).andExpect(status().isOk());

        EmailMessage sent = onlyEmail();
        assertTrue(sent.text().contains(message), sent.text());
        assertNull(sent.html());
    }

    @Test
    void googleOnlyAccount_usesTheAccountEmail() throws Exception {
        String email = newAddress();
        String token = loginAs(email, "Ana Google");
        jdbcTemplate.update("UPDATE users SET password_hash = NULL WHERE email = ?", email);

        ticket(token, body("DUVIDA", MESSAGE, null), uniqueIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyTo").value(email));

        assertEquals(email, onlyEmail().replyTo());
    }

    @Test
    void bearerThatIsNotAccepted_isUnauthorized_insteadOfBeingTreatedAsAnonymous() throws Exception {
        ticket("token-que-nao-vale", body("DUVIDA", MESSAGE, "ana@example.com"), uniqueIp())
                .andExpect(status().isUnauthorized());

        assertTrue(emailSender.sent().isEmpty());
    }

    // Helpers

    private EmailMessage onlyEmail() {
        assertEquals(1, emailSender.sent().size());
        return emailSender.sent().get(0);
    }

    private Map<String, Object> body(String category, String message, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("category", category);
        body.put("message", message);
        body.put("email", email);
        return body;
    }

    private ResultActions ticket(String token, Map<String, Object> body, String ip) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/support/tickets")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private String loginAs(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, name))))
                .andExpect(status().isAccepted());
        markVerified(email);
        emailSender.clear();

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private String newAddress() {
        return "support-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "10.40." + (n / 250) + "." + (n % 250 + 1);
    }
}
