package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.RecordingEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailChangeIntegrationTest extends AbstractIntegrationTest {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String PASSWORD = "senha12345";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emailSender;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Instant now;

    private record Session(String accessToken, String refreshToken) {
    }

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-27T15:00:00Z");
        when(clock.getZone()).thenReturn(SAO_PAULO);
        when(clock.instant()).thenAnswer(invocation -> now);
        emailSender.clear();
    }

    // CA1

    @Test
    void request_sendsTheLinkToTheNewEmail_andLeavesTheAccountEmailUnchanged() throws Exception {
        String email = registerNewUser();
        String newEmail = newAddress();
        Session session = login(email);

        requestChange(session, newEmail, PASSWORD).andExpect(status().isNoContent());

        List<EmailMessage> sent = emailSender.sent();
        assertEquals(1, sent.size());
        assertEquals(newEmail, sent.get(0).to());
        assertTrue(sent.get(0).text().contains("http://localhost:8080/#/confirmar-email?token="));

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.pendingEmail").value(newEmail));
    }

    @Test
    void request_storesTheNewEmailTrimmedAndInLowerCase() throws Exception {
        Session session = login(registerNewUser());
        String newEmail = newAddress();

        requestChange(session, "  " + newEmail.toUpperCase() + " ", PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(jsonPath("$.pendingEmail").value(newEmail));
        assertEquals(newEmail, emailSender.sent().get(0).to());
    }

    // CA2

    @Test
    void confirm_changesTheEmail_loginWorksWithTheNewOne_andTheOldAddressIsNotified() throws Exception {
        String oldEmail = registerNewUser();
        String newEmail = newAddress();
        Session session = login(oldEmail);
        requestChange(session, newEmail, PASSWORD).andExpect(status().isNoContent());
        String token = tokenFrom(emailSender.sent().get(0));

        confirm(token, null).andExpect(status().isNoContent());

        loginRequest(newEmail, PASSWORD).andExpect(status().isOk());
        loginRequest(oldEmail, PASSWORD).andExpect(status().isUnauthorized());

        EmailMessage notice = emailSender.sent().get(1);
        assertEquals(oldEmail, notice.to());
        assertTrue(notice.text().contains(newEmail));

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.pendingEmail").doesNotExist());
    }

    @Test
    void confirm_endsTheOtherSessions_butKeepsTheOneWhoseCookieArrived() throws Exception {
        String email = registerNewUser();
        Session a = login(email);
        Session b = login(email);
        requestChange(a, newAddress(), PASSWORD).andExpect(status().isNoContent());
        String token = tokenFrom(emailSender.sent().get(0));

        confirm(token, a.refreshToken()).andExpect(status().isNoContent());

        refresh(b).andExpect(status().isUnauthorized());
        refresh(a).andExpect(status().isOk());
    }

    @Test
    void confirm_withoutACookie_endsEverySession() throws Exception {
        String email = registerNewUser();
        Session a = login(email);
        requestChange(a, newAddress(), PASSWORD).andExpect(status().isNoContent());
        String token = tokenFrom(emailSender.sent().get(0));

        confirm(token, null).andExpect(status().isNoContent());

        refresh(a).andExpect(status().isUnauthorized());
    }

    @Test
    void confirm_whenTheNoticeToTheOldAddressFails_theChangeStillHolds() throws Exception {
        String oldEmail = registerNewUser();
        String newEmail = newAddress();
        Session session = login(oldEmail);
        requestChange(session, newEmail, PASSWORD).andExpect(status().isNoContent());
        String token = tokenFrom(emailSender.sent().get(0));

        emailSender.failFromNowOn(true);
        confirm(token, null).andExpect(status().isNoContent());
        emailSender.failFromNowOn(false);

        loginRequest(newEmail, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void accessToken_keepsWorkingAfterTheEmailChange() throws Exception {
        Session session = login(registerNewUser());
        String newEmail = newAddress();
        requestChange(session, newEmail, PASSWORD).andExpect(status().isNoContent());

        confirm(tokenFrom(emailSender.sent().get(0)), session.refreshToken()).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    // CA3

    @Test
    void request_withWrongPassword_sendsNothingAndStoresNothing() throws Exception {
        Session session = login(registerNewUser());

        requestChange(session, newAddress(), "senha-errada-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());

        assertTrue(emailSender.sent().isEmpty());
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(jsonPath("$.pendingEmail").doesNotExist());
    }

    @Test
    void request_wrongPasswordCountsOnThePasswordChangeLimit() throws Exception {
        Session session = login(registerNewUser());

        for (int i = 0; i < 5; i++) {
            requestChange(session, newAddress(), "senha-errada-123").andExpect(status().isBadRequest());
        }

        requestChange(session, newAddress(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // CA4

    @Test
    void request_forAnEmailOfAnotherAccount_isRefused_evenWithDifferentCase() throws Exception {
        String other = registerNewUser();
        Session session = login(registerNewUser());

        requestChange(session, other.toUpperCase(), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newEmail").exists());

        assertTrue(emailSender.sent().isEmpty());
    }

    // CA5

    @Test
    void confirm_expiredLink_isRefused_andANewRequestWorks() throws Exception {
        Session session = login(registerNewUser());
        String newEmail = newAddress();
        requestChange(session, newEmail, PASSWORD).andExpect(status().isNoContent());
        String token = tokenFrom(emailSender.sent().get(0));

        now = now.plus(Duration.ofHours(24)).plusSeconds(1);

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(jsonPath("$.pendingEmail").doesNotExist());
        confirm(token, null).andExpect(status().isBadRequest());

        requestChange(session, newEmail, PASSWORD).andExpect(status().isNoContent());
        confirm(tokenFrom(emailSender.sent().get(1)), null).andExpect(status().isNoContent());
    }

    @Test
    void confirm_usedLink_cannotBeUsedAgain() throws Exception {
        Session session = login(registerNewUser());
        requestChange(session, newAddress(), PASSWORD).andExpect(status().isNoContent());
        String token = tokenFrom(emailSender.sent().get(0));

        confirm(token, null).andExpect(status().isNoContent());
        confirm(token, null).andExpect(status().isBadRequest());
    }

    @Test
    void confirm_replacedLink_isRefused_andTheNewOneWorks() throws Exception {
        String email = registerNewUser();
        Session session = login(email);
        String first = newAddress();
        String second = newAddress();
        requestChange(session, first, PASSWORD).andExpect(status().isNoContent());
        requestChange(session, second, PASSWORD).andExpect(status().isNoContent());
        String oldToken = tokenFrom(emailSender.sent().get(0));
        String newToken = tokenFrom(emailSender.sent().get(1));

        confirm(oldToken, null).andExpect(status().isBadRequest());
        loginRequest(email, PASSWORD).andExpect(status().isOk());

        confirm(newToken, null).andExpect(status().isNoContent());
        loginRequest(second, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirm_unknownToken_isRefused_withoutDispatchingSessionExpired() throws Exception {
        confirm("token-que-nao-existe", null)
                .andExpect(status().isBadRequest());
    }

    // CA6

    @Test
    void pendingRequest_keepsLoginWorkingWithTheOldEmail() throws Exception {
        String email = registerNewUser();
        Session session = login(email);
        requestChange(session, newAddress(), PASSWORD).andExpect(status().isNoContent());

        loginRequest(email, PASSWORD).andExpect(status().isOk());
    }

    // bordas

    @Test
    void request_forTheCurrentEmail_isRefused_evenWithDifferentCase() throws Exception {
        String email = registerNewUser();
        Session session = login(email);

        requestChange(session, email, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newEmail").exists());
        requestChange(session, email.toUpperCase(), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newEmail").exists());

        assertTrue(emailSender.sent().isEmpty());
    }

    @Test
    void confirm_whenSomeoneTookTheNewEmailInTheMeantime_isRefused() throws Exception {
        String email = registerNewUser();
        Session session = login(email);
        String newEmail = newAddress();
        requestChange(session, newEmail, PASSWORD).andExpect(status().isNoContent());
        String token = tokenFrom(emailSender.sent().get(0));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(newEmail, PASSWORD, "Outra Pessoa"))))
                .andExpect(status().isCreated());
        markVerified(newEmail);

        confirm(token, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newEmail").exists());
        loginRequest(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void request_fourthWithinTheHour_isRateLimited() throws Exception {
        Session session = login(registerNewUser());

        for (int i = 0; i < 3; i++) {
            requestChange(session, newAddress(), PASSWORD).andExpect(status().isNoContent());
        }

        requestChange(session, newAddress(), PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void request_whenTheLinkCannotBeSent_failsAndStoresNoRequest() throws Exception {
        Session session = login(registerNewUser());
        emailSender.failFromNowOn(true);

        requestChange(session, newAddress(), PASSWORD).andExpect(status().isBadGateway());
        emailSender.failFromNowOn(false);

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(jsonPath("$.pendingEmail").doesNotExist());
    }

    @Test
    void request_withoutLogin_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/profile/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newEmail", newAddress(), "currentPassword", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // helpers

    private ResultActions requestChange(Session session, String newEmail, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/profile/email")
                .header(HttpHeaders.AUTHORIZATION, bearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("newEmail", newEmail, "currentPassword", password))));
    }

    private ResultActions confirm(String token, String refreshCookie) throws Exception {
        var request = post("/api/v1/auth/email/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token)));
        if (refreshCookie != null) {
            request.cookie(new Cookie(COOKIE_NAME, refreshCookie));
        }
        return mockMvc.perform(request);
    }

    private ResultActions refresh(Session session) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, session.refreshToken())));
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private String tokenFrom(EmailMessage message) {
        Matcher matcher = TOKEN.matcher(message.text());
        assertTrue(matcher.find(), "no token in the e-mail");
        return matcher.group(1);
    }

    private String newAddress() {
        return "novo-" + UUID.randomUUID() + "@example.com";
    }

    private String registerNewUser() throws Exception {
        String email = "troca-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Maria Silva"))))
                .andExpect(status().isCreated());
        markVerified(email);
        return email;
    }

    private Session login(String email) throws Exception {
        MvcResult result = loginRequest(email, PASSWORD).andExpect(status().isOk()).andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        String setCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow();
        String refreshToken = setCookie.substring((COOKIE_NAME + "=").length(), setCookie.indexOf(';'));
        return new Session(accessToken, refreshToken);
    }

    private String bearer(Session session) {
        return "Bearer " + session.accessToken();
    }
}
