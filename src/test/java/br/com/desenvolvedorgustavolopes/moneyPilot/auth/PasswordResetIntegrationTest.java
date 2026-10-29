package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.RecordingEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.rate-limit.login-failures-per-email=3")
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final String NEW_PASSWORD = "nova-senha-678";
    private static final String COOKIE_NAME = "refresh_token";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");
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
    void forgot_forARegisteredEmail_sendsTheLink() throws Exception {
        String email = registerVerified();

        forgot(email, uniqueIp())
                .andExpect(status().isAccepted())
                .andExpect(result -> assertEquals("", result.getResponse().getContentAsString()));

        List<EmailMessage> sent = emailSender.sent();
        assertEquals(1, sent.size());
        assertEquals(email, sent.get(0).to());
        assertTrue(sent.get(0).text().contains("/app/#/redefinir-senha?token="));
    }

    // CA2

    @Test
    void forgot_forAnUnknownEmail_answersTheSame_andSendsNothing() throws Exception {
        String email = registerVerified();
        emailSender.clear();
        MvcResult known = forgot(email, uniqueIp()).andReturn();
        emailSender.clear();

        MvcResult unknown = forgot(newAddress(), uniqueIp()).andReturn();

        assertEquals(known.getResponse().getStatus(), unknown.getResponse().getStatus());
        assertEquals(known.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
        assertEquals(0, emailSender.sent().size());
    }

    // CA3

    @Test
    void reset_changesThePassword_onlyTheNewOneLogsIn() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());

        reset(lastTokenSentTo(email), NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    // CA4

    @Test
    void reset_endsEverySessionAtTheNextRefresh() throws Exception {
        String email = registerVerified();
        String refreshToken = refreshTokenFrom(login(email, PASSWORD).andExpect(status().isOk()).andReturn());
        forgot(email, uniqueIp()).andExpect(status().isAccepted());

        reset(lastTokenSentTo(email), NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // CA5

    @Test
    void reset_expiredLink_isRefused_andANewOneWorks() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());
        String expired = lastTokenSentTo(email);

        now = now.plus(PasswordResetService.TOKEN_TTL);

        reset(expired, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(email, PASSWORD).andExpect(status().isOk());

        forgot(email, uniqueIp()).andExpect(status().isAccepted());
        reset(lastTokenSentTo(email), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void reset_justBeforeExpiring_stillWorks() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());

        now = now.plus(PasswordResetService.TOKEN_TTL).minusSeconds(1);

        reset(lastTokenSentTo(email), NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void reset_usedLink_cannotBeUsedAgain() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());
        String token = lastTokenSentTo(email);

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        reset(token, "outra-senha-999").andExpect(status().isBadRequest());
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void reset_linkReplacedByANewRequest_isRefused_andTheNewOneWorks() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());
        String replaced = lastTokenSentTo(email);
        forgot(email, uniqueIp()).andExpect(status().isAccepted());
        String current = lastTokenSentTo(email);

        assertNotEquals(replaced, current);
        reset(replaced, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(current, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    // CA6

    @Test
    void reset_verifiesAnAccountThatWasNotVerifiedYet() throws Exception {
        String email = registerUnverified();
        login(email, PASSWORD).andExpect(status().isForbidden());
        forgot(email, uniqueIp()).andExpect(status().isAccepted());

        reset(lastTokenSentTo(email), NEW_PASSWORD).andExpect(status().isNoContent());

        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM users WHERE email = ?", Timestamp.class, email));
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    // CA7

    @Test
    void reset_warnsTheAccountEmailThatThePasswordChanged() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());
        String token = lastTokenSentTo(email);
        emailSender.clear();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        List<EmailMessage> sent = emailSender.sent();
        assertEquals(1, sent.size());
        assertEquals(email, sent.get(0).to());
        assertTrue(sent.get(0).text().contains("foi redefinida"));
    }

    @Test
    void reset_whenTheNoticeCannotBeSent_thePasswordStillChanges() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());
        String token = lastTokenSentTo(email);
        emailSender.failFromNowOn(true);

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        emailSender.failFromNowOn(false);
        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    // Bordas

    @Test
    void reset_unlocksALoginBlockedByWrongPasswords() throws Exception {
        String email = registerVerified();
        for (int i = 0; i < 3; i++) {
            login(email, "senha-errada").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD).andExpect(status().isTooManyRequests());
        forgot(email, uniqueIp()).andExpect(status().isAccepted());

        reset(lastTokenSentTo(email), NEW_PASSWORD).andExpect(status().isNoContent());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void reset_withAShortPassword_isRefusedWithFieldError_andTheLinkStillWorks() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());
        String token = lastTokenSentTo(email);

        reset(token, "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void reset_withTheCurrentPassword_isAccepted() throws Exception {
        String email = registerVerified();
        forgot(email, uniqueIp()).andExpect(status().isAccepted());

        reset(lastTokenSentTo(email), PASSWORD).andExpect(status().isNoContent());

        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void reset_unknownToken_isRefused() throws Exception {
        reset("token-que-nao-existe", NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void forgot_withDifferentCaseAndSpaces_findsTheAccount() throws Exception {
        String email = registerVerified();

        forgot("  " + email.toUpperCase() + " ", uniqueIp()).andExpect(status().isAccepted());

        assertEquals(email, emailSender.sent().get(0).to());
    }

    @Test
    void forgot_whenTheLinkCannotBeSent_stillAnswers202() throws Exception {
        String email = registerVerified();
        emailSender.failFromNowOn(true);

        forgot(email, uniqueIp()).andExpect(status().isAccepted());
    }

    @Test
    void forgot_fourthForTheSameEmailWithinTheHour_isRateLimited() throws Exception {
        String email = newAddress();

        for (int i = 0; i < 3; i++) {
            forgot(email, uniqueIp()).andExpect(status().isAccepted());
        }
        forgot(email, uniqueIp()).andExpect(status().isTooManyRequests());

        now = now.plus(Duration.ofHours(1));

        forgot(email, uniqueIp()).andExpect(status().isAccepted());
    }

    @Test
    void forgot_sixthFromTheSameIpWithinTheHour_isRateLimited() throws Exception {
        String ip = uniqueIp();

        for (int i = 0; i < 5; i++) {
            forgot(newAddress(), ip).andExpect(status().isAccepted());
        }
        forgot(newAddress(), ip).andExpect(status().isTooManyRequests());
    }

    private String registerVerified() throws Exception {
        String email = registerUnverified();
        markVerified(email);
        emailSender.clear();
        return email;
    }

    private String registerUnverified() throws Exception {
        String email = newAddress();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Maria Silva"))))
                .andExpect(status().isAccepted());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private ResultActions forgot(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/forgot")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", newPassword))));
    }

    private String refreshTokenFrom(MvcResult login) {
        String setCookie = login.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow();
        return setCookie.substring((COOKIE_NAME + "=").length(), setCookie.indexOf(';'));
    }

    private String lastTokenSentTo(String email) {
        List<EmailMessage> sent = emailSender.sent().stream()
                .filter(message -> message.to().equals(email))
                .toList();
        Matcher matcher = TOKEN.matcher(sent.get(sent.size() - 1).text());
        assertTrue(matcher.find(), "no link in the last e-mail to " + email);
        return matcher.group(1);
    }

    private String newAddress() {
        return "reset-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "10.30." + (n / 250) + "." + (n % 250 + 1);
    }
}
