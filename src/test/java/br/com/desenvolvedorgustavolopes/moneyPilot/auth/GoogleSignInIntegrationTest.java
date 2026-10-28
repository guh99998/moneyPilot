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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.rate-limit.login-per-ip=4")
class GoogleSignInIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final String NEW_PASSWORD = "nova-senha-678";
    private static final String COOKIE_NAME = "refresh_token";
    private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeGoogleTokenVerifier google;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        emailSender.clear();
    }

    // CA1

    @Test
    void signIn_withANewEmail_createsAVerifiedAccountWithoutPassword() throws Exception {
        GoogleIdentity identity = identity();

        MvcResult result = signIn(identity, uniqueIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(identity.email()))
                .andReturn();

        assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith(COOKIE_NAME + "=") && cookie.contains("HttpOnly")));

        Map<String, Object> row = userRow(identity.email());
        assertNotNull(row.get("email_verified_at"));
        assertNull(row.get("password_hash"));
        assertEquals(identity.subject(), row.get("google_subject"));
        assertEquals(identity.name(), row.get("name"));

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.googleEmail").value(identity.email()))
                .andExpect(jsonPath("$.avatarUrl").value(identity.picture()));
    }

    @Test
    void signIn_withoutAGoogleName_usesTheStartOfTheEmail() throws Exception {
        String email = "sem-nome-" + UUID.randomUUID() + "@example.com";

        signIn(new GoogleIdentity(newSubject(), email, null, null), uniqueIp()).andExpect(status().isOk());

        assertEquals(email.substring(0, email.indexOf('@')), userRow(email).get("name"));
    }

    // CA2

    @Test
    void signIn_withTheEmailOfAnExistingAccount_entersTheSameAccountAndKeepsItsData() throws Exception {
        String email = registerVerified("Maria Silva");
        long userId = userId(email);
        GoogleIdentity identity = new GoogleIdentity(newSubject(), email, "Maria no Google", "https://lh3.example/maria.png");

        MvcResult result = signIn(identity, uniqueIp()).andExpect(status().isOk()).andReturn();

        assertEquals(userId, userId(email));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email));
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(result)))
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.googleEmail").value(email));
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void signIn_withMixedCaseEmailFromGoogle_findsTheAccount() throws Exception {
        String email = registerVerified("Maria Silva");
        long userId = userId(email);

        signIn(new GoogleIdentity(newSubject(), email.toUpperCase(), "Maria", null), uniqueIp())
                .andExpect(status().isOk());

        assertEquals(userId, userId(email));
    }

    // CA3

    @Test
    void passwordLogin_onAGoogleOnlyAccount_answersLikeAWrongPassword() throws Exception {
        GoogleIdentity identity = identity();
        signIn(identity, uniqueIp()).andExpect(status().isOk());
        String registered = registerVerified("Joao");

        MvcResult googleOnly = login(identity.email(), PASSWORD).andExpect(status().isUnauthorized()).andReturn();
        MvcResult wrongPassword = login(registered, "senha-errada-123").andExpect(status().isUnauthorized()).andReturn();

        assertEquals(
                JsonPath.<String>read(wrongPassword.getResponse().getContentAsString(), "$.message"),
                JsonPath.<String>read(googleOnly.getResponse().getContentAsString(), "$.message"));
    }

    // CA4

    @Test
    void signIn_withARejectedCredential_answers401_andCreatesNothing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .with(request -> {
                            request.setRemoteAddr(uniqueIp());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", "token-que-o-google-nao-emitiu"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signIn_withoutACredential_answers400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // Bordas

    @Test
    void signIn_onAnUnverifiedAccount_verifiesIt_andTheOldPasswordStopsWorking() throws Exception {
        String email = registerUnverified("Maria Silva");

        signIn(new GoogleIdentity(newSubject(), email, "Maria", null), uniqueIp()).andExpect(status().isOk());

        Map<String, Object> row = userRow(email);
        assertNotNull(row.get("email_verified_at"));
        assertNull(row.get("password_hash"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = ?", Integer.class, row.get("id")));
        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void signIn_afterTheGoogleEmailChanged_entersTheSameAccountBySubject() throws Exception {
        GoogleIdentity first = identity();
        signIn(first, uniqueIp()).andExpect(status().isOk());
        long userId = userId(first.email());
        String newGoogleEmail = "novo-" + UUID.randomUUID() + "@example.com";

        signIn(new GoogleIdentity(first.subject(), newGoogleEmail, first.name(), first.picture()), uniqueIp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(first.email()));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND email = ? AND google_email = ?",
                Integer.class, userId, first.email(), newGoogleEmail));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, newGoogleEmail));
    }

    @Test
    void signIn_withTheEmailOfAnAccountLinkedToAnotherGoogle_answers409() throws Exception {
        GoogleIdentity owner = identity();
        signIn(owner, uniqueIp()).andExpect(status().isOk());

        signIn(new GoogleIdentity(newSubject(), owner.email(), "Outra pessoa", null), uniqueIp())
                .andExpect(status().isConflict());

        assertEquals(owner.subject(), userRow(owner.email()).get("google_subject"));
    }

    @Test
    void signIn_isLimitedPerIp() throws Exception {
        String ip = uniqueIp();
        for (int i = 0; i < 4; i++) {
            signIn(identity(), ip).andExpect(status().isOk());
        }

        signIn(identity(), ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void config_isPublic_andExposesTheClientId() throws Exception {
        mockMvc.perform(get("/api/v1/auth/google/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("test-client-id.apps.googleusercontent.com"));
    }

    // CA5 — vincular

    @Test
    void link_withTheRightPassword_linksTheGoogleAccount() throws Exception {
        String email = registerVerified("Maria Silva");
        String token = accessToken(email);
        GoogleIdentity identity = identity();

        link(token, identity, PASSWORD).andExpect(status().isNoContent());

        Map<String, Object> row = userRow(email);
        assertEquals(identity.subject(), row.get("google_subject"));
        assertEquals(identity.email(), row.get("google_email"));
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.googleEmail").value(identity.email()))
                .andExpect(jsonPath("$.avatarUrl").value(identity.picture()));
        signIn(identity, uniqueIp()).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void link_withTheWrongPassword_answers400_andCountsTheFailure() throws Exception {
        String email = registerVerified("Maria Silva");
        String token = accessToken(email);
        GoogleIdentity identity = identity();

        for (int i = 0; i < 5; i++) {
            link(token, identity, "senha-errada-123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
        }

        link(token, identity, PASSWORD).andExpect(status().isTooManyRequests());
        assertNull(userRow(email).get("google_subject"));
    }

    @Test
    void link_withAGoogleAlreadyUsedByAnotherAccount_answers409() throws Exception {
        GoogleIdentity taken = identity();
        signIn(taken, uniqueIp()).andExpect(status().isOk());
        String email = registerVerified("Maria Silva");

        link(accessToken(email), taken, PASSWORD).andExpect(status().isConflict());

        assertNull(userRow(email).get("google_subject"));
    }

    @Test
    void link_withADifferentGoogleEmail_isAccepted() throws Exception {
        String email = registerVerified("Maria Silva");
        GoogleIdentity identity = identity();
        assertFalse(identity.email().equals(email));

        link(accessToken(email), identity, PASSWORD).andExpect(status().isNoContent());

        assertEquals(email, userRow(email).get("email"));
        assertEquals(identity.email(), userRow(email).get("google_email"));
    }

    @Test
    void link_onAnAccountThatIsAlreadyLinked_answers409() throws Exception {
        String email = registerVerified("Maria Silva");
        String token = accessToken(email);
        link(token, identity(), PASSWORD).andExpect(status().isNoContent());

        link(token, identity(), PASSWORD).andExpect(status().isConflict());
    }

    @Test
    void link_withARejectedCredential_answers400_notASessionError() throws Exception {
        String email = registerVerified("Maria Silva");

        mockMvc.perform(post("/api/v1/profile/google")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", "falso", "currentPassword", PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.credential").exists());
    }

    @Test
    void link_requiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/profile/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", "x", "currentPassword", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // CA6 — desvincular

    @Test
    void unlink_withThePassword_removesTheLinkAndThePhoto() throws Exception {
        String email = registerVerified("Maria Silva");
        String token = accessToken(email);
        link(token, identity(), PASSWORD).andExpect(status().isNoContent());

        unlink(token, PASSWORD).andExpect(status().isNoContent());

        Map<String, Object> row = userRow(email);
        assertNull(row.get("google_subject"));
        assertNull(row.get("google_email"));
        assertNull(row.get("avatar_url"));
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.googleEmail").doesNotExist())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void unlink_withTheWrongPassword_answers400() throws Exception {
        String email = registerVerified("Maria Silva");
        String token = accessToken(email);
        link(token, identity(), PASSWORD).andExpect(status().isNoContent());

        unlink(token, "senha-errada-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());

        assertNotNull(userRow(email).get("google_subject"));
    }

    @Test
    void unlink_onAnAccountWithoutPassword_answers409() throws Exception {
        GoogleIdentity identity = identity();
        String token = JsonPath.read(
                signIn(identity, uniqueIp()).andReturn().getResponse().getContentAsString(), "$.token");

        unlink(token, PASSWORD).andExpect(status().isConflict());

        assertNotNull(userRow(identity.email()).get("google_subject"));
    }

    @Test
    void unlink_onAnAccountThatIsNotLinked_answers409() throws Exception {
        String email = registerVerified("Maria Silva");

        unlink(accessToken(email), PASSWORD).andExpect(status().isConflict());
    }

    // CA7 — foto

    @Test
    void signIn_refreshesThePhotoEveryTime() throws Exception {
        GoogleIdentity first = identity();
        signIn(first, uniqueIp()).andExpect(status().isOk());

        signIn(new GoogleIdentity(first.subject(), first.email(), first.name(), "https://lh3.example/nova.png"), uniqueIp())
                .andExpect(status().isOk());

        assertEquals("https://lh3.example/nova.png", userRow(first.email()).get("avatar_url"));
    }

    @Test
    void hideAvatar_keepsItHiddenOnTheNextSignIn_untilTheGooglePhotoIsRequestedAgain() throws Exception {
        GoogleIdentity identity = identity();
        String token = JsonPath.read(
                signIn(identity, uniqueIp()).andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(delete("/api/v1/profile/avatar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.avatarHidden").value(true));

        signIn(new GoogleIdentity(identity.subject(), identity.email(), identity.name(), "https://lh3.example/outra.png"), uniqueIp())
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());

        mockMvc.perform(post("/api/v1/profile/avatar/google").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.avatarUrl").value(identity.picture()))
                .andExpect(jsonPath("$.avatarHidden").value(false));
    }

    @Test
    void useGoogleAvatar_withoutALink_answers409() throws Exception {
        String email = registerVerified("Maria Silva");

        mockMvc.perform(post("/api/v1/profile/avatar/google").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(email)))
                .andExpect(status().isConflict());
    }

    // CA8 — criar senha pelo reset

    @Test
    void passwordReset_onAGoogleOnlyAccount_givesItAPassword() throws Exception {
        GoogleIdentity identity = identity();
        signIn(identity, uniqueIp()).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .with(request -> {
                            request.setRemoteAddr(uniqueIp());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", identity.email()))))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", lastTokenSentTo(identity.email()), "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        login(identity.email(), NEW_PASSWORD).andExpect(status().isOk());
        signIn(identity, uniqueIp()).andExpect(status().isOk());
    }

    @Test
    void passwordAndEmailChanges_onAnAccountWithoutPassword_areRefusedOnTheCurrentPasswordField() throws Exception {
        String token = JsonPath.read(
                signIn(identity(), uniqueIp()).andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", "qualquer-coisa-1", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
        mockMvc.perform(post("/api/v1/profile/email")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newEmail", "outro@example.com", "currentPassword", "qualquer-coisa-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
    }

    // Helpers

    private GoogleIdentity identity() {
        return new GoogleIdentity(
                newSubject(),
                "google-" + UUID.randomUUID() + "@example.com",
                "Ana Souza",
                "https://lh3.example/" + UUID.randomUUID() + ".png");
    }

    private String newSubject() {
        return "sub-" + UUID.randomUUID();
    }

    private ResultActions signIn(GoogleIdentity identity, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/google")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("credential", google.credentialFor(identity)))));
    }

    private ResultActions link(String token, GoogleIdentity identity, String currentPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/profile/google")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("credential", google.credentialFor(identity), "currentPassword", currentPassword))));
    }

    private ResultActions unlink(String token, String currentPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/profile/google/unlink")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("currentPassword", currentPassword))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(uniqueIp());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private String accessToken(String email) throws Exception {
        MvcResult result = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String bearer(MvcResult signInResult) throws Exception {
        return "Bearer " + JsonPath.<String>read(signInResult.getResponse().getContentAsString(), "$.token");
    }

    private String registerVerified(String name) throws Exception {
        String email = registerUnverified(name);
        markVerified(email);
        emailSender.clear();
        return email;
    }

    private String registerUnverified(String name) throws Exception {
        String email = "google-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, name))))
                .andExpect(status().isAccepted());
        return email;
    }

    private Map<String, Object> userRow(String email) {
        return jdbcTemplate.queryForMap("SELECT * FROM users WHERE email = ?", email);
    }

    private long userId(String email) {
        return ((Number) userRow(email).get("id")).longValue();
    }

    private String lastTokenSentTo(String email) {
        List<EmailMessage> sent = emailSender.sent().stream()
                .filter(message -> message.to().equals(email))
                .toList();
        Matcher matcher = TOKEN.matcher(sent.get(sent.size() - 1).text());
        assertTrue(matcher.find(), "no link in the last e-mail to " + email);
        return matcher.group(1);
    }

    private String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "10.40." + (n / 250) + "." + (n % 250 + 1);
    }
}
