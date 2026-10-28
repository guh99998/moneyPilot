package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.RecordingEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha12345";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    // CA1

    @Test
    void register_createsAnUnverifiedAccount_andSendsTheLink() throws Exception {
        String email = newAddress();

        register(email, PASSWORD, "Maria Silva")
                .andExpect(status().isAccepted())
                .andExpect(result -> assertEquals("", result.getResponse().getContentAsString()));

        assertNull(verifiedAt(email));
        List<EmailMessage> sent = emailSender.sent();
        assertEquals(1, sent.size());
        assertEquals(email, sent.get(0).to());
        assertTrue(sent.get(0).text().contains("/#/verificar-email?token="));
    }

    // CA2

    @Test
    void login_beforeVerifying_isRefusedWith403_onlyWithTheRightPassword() throws Exception {
        String email = registerUnverified();

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        login(email, "senha-errada").andExpect(status().isUnauthorized());
    }

    // CA3

    @Test
    void confirm_verifiesTheAccount_andLoginWorks() throws Exception {
        String email = registerUnverified();

        confirm(lastTokenSentTo(email)).andExpect(status().isNoContent());

        assertEquals(now, verifiedAt(email));
        login(email, PASSWORD).andExpect(status().isOk());
    }

    // CA4

    @Test
    void confirm_expiredLink_isRefused_andANewOneWorks() throws Exception {
        String email = registerUnverified();
        String expired = lastTokenSentTo(email);

        now = now.plus(EmailVerificationService.TOKEN_TTL);

        confirm(expired).andExpect(status().isBadRequest());
        assertNull(verifiedAt(email));

        resend(email, uniqueIp()).andExpect(status().isAccepted());
        confirm(lastTokenSentTo(email)).andExpect(status().isNoContent());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirm_linkReplacedByAResend_isRefused_andTheNewOneWorks() throws Exception {
        String email = registerUnverified();
        String replaced = lastTokenSentTo(email);

        resend(email, uniqueIp()).andExpect(status().isAccepted());
        String current = lastTokenSentTo(email);

        assertNotEquals(replaced, current);
        confirm(replaced).andExpect(status().isBadRequest());
        confirm(current).andExpect(status().isNoContent());
    }

    @Test
    void confirm_unknownToken_isRefused() throws Exception {
        confirm("token-que-nao-existe").andExpect(status().isBadRequest());
    }

    // CA5

    @Test
    void accountsThatExistedBeforeVerification_keepLoggingIn() throws Exception {
        String email = newAddress();
        Instant createdAt = Instant.parse("2025-03-10T12:00:00Z");
        // Estado em que a V12 deixa quem já tinha conta: verificado desde a criação.
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, name, created_at, email_verified_at)
                VALUES (?, ?, ?, ?, ?)
                """, email, passwordEncoder.encode(PASSWORD), "Conta Antiga",
                Timestamp.from(createdAt), Timestamp.from(createdAt));

        login(email, PASSWORD).andExpect(status().isOk());
        login(email.toUpperCase(), PASSWORD).andExpect(status().isOk());
    }

    // CA6

    @Test
    void resend_answersTheSameForUnknownUnverifiedAndVerifiedEmails() throws Exception {
        String unverified = registerUnverified();
        String verified = registerUnverified();
        markVerified(verified);
        emailSender.clear();

        MvcResult unknown = resend(newAddress(), uniqueIp()).andReturn();
        MvcResult pending = resend(unverified, uniqueIp()).andReturn();
        MvcResult active = resend(verified, uniqueIp()).andReturn();

        for (MvcResult result : List.of(unknown, pending, active)) {
            assertEquals(202, result.getResponse().getStatus());
            assertEquals("", result.getResponse().getContentAsString());
        }

        List<EmailMessage> sent = emailSender.sent();
        assertEquals(2, sent.size());
        assertEquals(unverified, sent.get(0).to());
        assertTrue(sent.get(0).text().contains("/#/verificar-email?token="));
        assertEquals(verified, sent.get(1).to());
        assertTrue(sent.get(1).text().contains("já está ativa"));
    }

    // CA7

    @Test
    void register_withAVerifiedEmail_answersLikeANewOne_createsNothing_andWarnsTheOwner() throws Exception {
        String email = registerUnverified();
        markVerified(email);
        emailSender.clear();
        MvcResult fresh = register(newAddress(), PASSWORD, "Outra Pessoa").andReturn();
        emailSender.clear();

        MvcResult taken = register(email, "outra-senha-123", "Outra Pessoa").andReturn();

        assertEquals(fresh.getResponse().getStatus(), taken.getResponse().getStatus());
        assertEquals(fresh.getResponse().getContentAsString(), taken.getResponse().getContentAsString());
        assertEquals(1, accountsWith(email));
        login(email, PASSWORD).andExpect(status().isOk());
        login(email, "outra-senha-123").andExpect(status().isUnauthorized());

        List<EmailMessage> sent = emailSender.sent();
        assertEquals(1, sent.size());
        assertEquals(email, sent.get(0).to());
        assertTrue(sent.get(0).text().contains("tentou criar uma conta"));
    }

    @Test
    void register_withAVerifiedEmail_warnsTheOwnerAtMostOncePerHour() throws Exception {
        String email = registerUnverified();
        markVerified(email);
        emailSender.clear();

        register(email, PASSWORD, "Outra Pessoa").andExpect(status().isAccepted());
        register(email, PASSWORD, "Outra Pessoa").andExpect(status().isAccepted());
        assertEquals(1, emailSender.sent().size());

        now = now.plus(Duration.ofHours(1));

        register(email, PASSWORD, "Outra Pessoa").andExpect(status().isAccepted());
        assertEquals(2, emailSender.sent().size());
    }

    // CA8

    @Test
    void email_isTheSameRegardlessOfCase() throws Exception {
        String lower = newAddress();
        String mixed = "  " + lower.substring(0, 1).toUpperCase() + lower.substring(1).replace("@example.com", "@Example.COM") + " ";

        register(mixed, PASSWORD, "Maria Silva").andExpect(status().isAccepted());

        assertEquals(1, accountsWith(lower));
        assertEquals(lower, emailSender.sent().get(0).to());

        confirm(lastTokenSentTo(lower)).andExpect(status().isNoContent());
        login(lower.toUpperCase(), PASSWORD).andExpect(status().isOk());
        emailSender.clear();

        register(lower.toUpperCase(), "outra-senha-123", "Outra Pessoa").andExpect(status().isAccepted());

        assertEquals(1, accountsWith(lower));
        assertTrue(emailSender.sent().get(0).text().contains("tentou criar uma conta"));
    }

    // Bordas

    @Test
    void confirm_usedLinkAgain_stillAnswers204() throws Exception {
        String email = registerUnverified();
        String token = lastTokenSentTo(email);

        confirm(token).andExpect(status().isNoContent());
        Instant firstVerification = verifiedAt(email);
        now = now.plus(Duration.ofDays(2));

        confirm(token).andExpect(status().isNoContent());
        assertEquals(firstVerification, verifiedAt(email));
    }

    @Test
    void confirm_whileLoggedInAsSomeoneElse_verifiesTheLinkAccount_andKeepsTheSession() throws Exception {
        String other = registerUnverified();
        markVerified(other);
        String accessToken = JsonPath.read(login(other, PASSWORD).andReturn().getResponse().getContentAsString(), "$.token");
        String email = registerUnverified();

        mockMvc.perform(post("/api/v1/auth/verification/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", lastTokenSentTo(email)))))
                .andExpect(status().isNoContent());

        assertNotNull(verifiedAt(email));
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(other));
    }

    @Test
    void register_againForAnUnverifiedEmail_reusesTheAccount_withTheNewPassword_andANewLink() throws Exception {
        String email = registerUnverified();
        String oldToken = lastTokenSentTo(email);

        register(email, "outra-senha-123", "Nome Novo").andExpect(status().isAccepted());

        assertEquals(1, accountsWith(email));
        confirm(oldToken).andExpect(status().isBadRequest());
        confirm(lastTokenSentTo(email)).andExpect(status().isNoContent());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, "outra-senha-123").andExpect(status().isOk());
        assertEquals("Nome Novo", jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE email = ?", String.class, email));
    }

    @Test
    void register_whenTheLinkCannotBeSent_answers502_andStoresNothing() throws Exception {
        String email = newAddress();
        emailSender.failFromNowOn(true);

        register(email, PASSWORD, "Maria Silva").andExpect(status().isBadGateway());

        assertEquals(0, accountsWith(email));
    }

    @Test
    void resend_whenTheLinkCannotBeSent_stillAnswers202() throws Exception {
        String email = registerUnverified();
        emailSender.failFromNowOn(true);

        resend(email, uniqueIp()).andExpect(status().isAccepted());
    }

    @Test
    void resend_fourthForTheSameEmailWithinTheHour_isRateLimited() throws Exception {
        String email = newAddress();

        for (int i = 0; i < 3; i++) {
            resend(email, uniqueIp()).andExpect(status().isAccepted());
        }
        resend(email, uniqueIp()).andExpect(status().isTooManyRequests());

        now = now.plus(Duration.ofHours(1));

        resend(email, uniqueIp()).andExpect(status().isAccepted());
    }

    @Test
    void resend_eleventhFromTheSameIpWithinTheHour_isRateLimited() throws Exception {
        String ip = uniqueIp();

        for (int i = 0; i < 10; i++) {
            resend(newAddress(), ip).andExpect(status().isAccepted());
        }
        resend(newAddress(), ip).andExpect(status().isTooManyRequests());
    }

    private String registerUnverified() throws Exception {
        String email = newAddress();
        register(email, PASSWORD, "Maria Silva").andExpect(status().isAccepted());
        return email;
    }

    private ResultActions register(String email, String password, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, name))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private ResultActions resend(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verification/resend")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private String lastTokenSentTo(String email) {
        List<EmailMessage> sent = emailSender.sent().stream()
                .filter(message -> message.to().equals(email))
                .toList();
        Matcher matcher = TOKEN.matcher(sent.get(sent.size() - 1).text());
        assertTrue(matcher.find(), "no link in the last e-mail to " + email);
        return matcher.group(1);
    }

    private Instant verifiedAt(String email) {
        Timestamp verifiedAt = jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM users WHERE email = ?", Timestamp.class, email);
        return verifiedAt == null ? null : verifiedAt.toInstant();
    }

    private int accountsWith(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }

    private String newAddress() {
        return "verifica-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "10.20." + (n / 250) + "." + (n % 250 + 1);
    }
}
