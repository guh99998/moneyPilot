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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 012: exclusão da própria conta. */
class AccountDeletionIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final String COOKIE_NAME = "refresh_token";
    private static final String DELETION = "/api/v1/profile/deletion";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    /** Tabelas com {@code user_id}; transactions entra pelas contas. */
    private static final List<String> USER_TABLES = List.of(
            "bills", "bill_recurrences", "budgets", "dismissed_alerts", "support_tickets", "categories",
            "accounts", "refresh_tokens", "email_verification_tokens", "password_reset_tokens", "email_change_tokens");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private FakeGoogleTokenVerifier google;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        emailSender.clear();
    }

    // CA1, CA6

    @Test
    void delete_withTheRightPassword_removesTheAccountAndEveryRowOfIt() throws Exception {
        Session session = registerAndLogin();
        seedEverything(session.userId());
        assertTrue(rowsOf(session.userId()) > 0);

        delete(session, Map.of("currentPassword", PASSWORD)).andExpect(status().isNoContent());

        assertEquals(0, rowsOf(session.userId()));
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
        assertEquals(0, count("SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id = t.account_id WHERE a.user_id = ?", session.userId()));
    }

    @Test
    void delete_clearsTheRefreshCookie() throws Exception {
        Session session = registerAndLogin();

        MvcResult result = delete(session, Map.of("currentPassword", PASSWORD)).andExpect(status().isNoContent()).andReturn();

        assertTrue(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .anyMatch(cookie -> cookie.startsWith(COOKIE_NAME + "=;") && cookie.contains("Max-Age=0")));
    }

    @Test
    void delete_keepsTheGlobalCategories() throws Exception {
        Session session = registerAndLogin();
        int global = count("SELECT COUNT(*) FROM categories WHERE user_id IS NULL");

        delete(session, Map.of("currentPassword", PASSWORD)).andExpect(status().isNoContent());

        assertTrue(global > 0);
        assertEquals(global, count("SELECT COUNT(*) FROM categories WHERE user_id IS NULL"));
    }

    // CA2

    @Test
    void delete_withAWrongPassword_deletesNothingAndFailsOnTheField() throws Exception {
        Session session = registerAndLogin();
        seedEverything(session.userId());
        int before = rowsOf(session.userId());
        emailSender.clear();

        delete(session, Map.of("currentPassword", "senha errada 123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());

        assertEquals(before, rowsOf(session.userId()));
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
        assertTrue(emailSender.sent().stream().noneMatch(m -> m.to().equals(session.email())));
    }

    @Test
    void delete_afterTooManyWrongPasswords_isTooManyRequests() throws Exception {
        Session session = registerAndLogin();

        for (int i = 0; i < 5; i++) {
            delete(session, Map.of("currentPassword", "senha errada 123")).andExpect(status().isBadRequest());
        }

        delete(session, Map.of("currentPassword", "senha errada 123")).andExpect(status().isTooManyRequests());
        delete(session, Map.of("currentPassword", PASSWORD)).andExpect(status().isTooManyRequests());
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
    }

    @Test
    void delete_withoutAnyConfirmation_isRejectedOnTheField() throws Exception {
        Session session = registerAndLogin();

        delete(session, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
    }

    @Test
    void delete_ofAPasswordAccountWithAGoogleCredentialOnly_isRejected() throws Exception {
        Session session = registerAndLogin();
        GoogleIdentity identity = new GoogleIdentity(UUID.randomUUID().toString(), session.email(), "Maria", null);

        delete(session, Map.of("credential", google.credentialFor(identity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.credential").exists());

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
    }

    // CA3

    @Test
    void delete_ofAGoogleOnlyAccount_withTheLinkedGoogle_deletesIt() throws Exception {
        GoogleIdentity identity = identity();
        Session session = googleSession(identity);
        seedEverything(session.userId());

        delete(session, Map.of("credential", google.credentialFor(identity))).andExpect(status().isNoContent());

        assertEquals(0, rowsOf(session.userId()));
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
    }

    @Test
    void delete_ofAGoogleOnlyAccount_withAnotherGoogle_isRejected() throws Exception {
        Session session = googleSession(identity());

        delete(session, Map.of("credential", google.credentialFor(identity())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.credential").exists());

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
    }

    @Test
    void delete_ofAGoogleOnlyAccount_withAnInvalidCredential_isRejected() throws Exception {
        Session session = googleSession(identity());

        delete(session, Map.of("credential", "nao-e-um-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.credential").exists());

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
    }

    @Test
    void delete_ofAGoogleOnlyAccount_withAPassword_isRejected() throws Exception {
        Session session = googleSession(identity());

        delete(session, Map.of("currentPassword", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
    }

    // CA4

    @Test
    void afterDeleting_theOldEmailCannotLogIn_andCanRegisterAgain() throws Exception {
        Session session = registerAndLogin();
        delete(session, Map.of("currentPassword", PASSWORD)).andExpect(status().isNoContent());

        login(session.email(), PASSWORD).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(session.email(), PASSWORD, "Maria de novo"))))
                .andExpect(status().isAccepted());
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE email = ?", session.email()));
    }

    // CA5

    @Test
    void afterDeleting_otherSessionsAreRefused() throws Exception {
        Session first = registerAndLogin();
        Session second = login(first.email());

        delete(first, Map.of("currentPassword", PASSWORD)).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, second.refreshToken())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + second.token()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + first.token()))
                .andExpect(status().isUnauthorized());
    }

    // CA6

    @Test
    void delete_sendsTheNoticeToTheAccountEmail() throws Exception {
        Session session = registerAndLogin();
        emailSender.clear();

        delete(session, Map.of("currentPassword", PASSWORD)).andExpect(status().isNoContent());

        List<EmailMessage> notices = emailSender.sent().stream().filter(m -> m.to().equals(session.email())).toList();
        assertEquals(1, notices.size());
        assertEquals("Sua conta foi excluída", notices.get(0).subject());
    }

    @Test
    void delete_whenTheNoticeFails_stillDeletesTheAccount() throws Exception {
        Session session = registerAndLogin();
        seedEverything(session.userId());
        emailSender.failFromNowOn(true);

        delete(session, Map.of("currentPassword", PASSWORD)).andExpect(status().isNoContent());

        assertEquals(0, rowsOf(session.userId()));
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id = ?", session.userId()));
    }

    // Isolamento

    @Test
    void delete_neverTouchesAnotherUsersData() throws Exception {
        Session mine = registerAndLogin();
        Session other = registerAndLogin();
        seedEverything(mine.userId());
        seedEverything(other.userId());
        int otherBefore = rowsOf(other.userId());
        int otherTransactions = count("SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id = t.account_id WHERE a.user_id = ?", other.userId());

        delete(mine, Map.of("currentPassword", PASSWORD)).andExpect(status().isNoContent());

        assertEquals(otherBefore, rowsOf(other.userId()));
        assertEquals(otherTransactions, count("SELECT COUNT(*) FROM transactions t JOIN accounts a ON a.id = t.account_id WHERE a.user_id = ?", other.userId()));
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", other.userId()));
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isOk());
    }

    @Test
    void delete_withoutASession_isUnauthorized() throws Exception {
        mockMvc.perform(post(DELETION).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // helpers

    private record Session(long userId, String email, String token, String refreshToken) {
    }

    private ResultActions delete(Session session, Map<String, String> body) throws Exception {
        return mockMvc.perform(post(DELETION)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
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

    private Session login(String email) throws Exception {
        MvcResult result = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
        return session(result, email);
    }

    private Session registerAndLogin() throws Exception {
        String email = "exclusao-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Maria Silva"))))
                .andExpect(status().isAccepted());
        markVerified(email);
        return login(email);
    }

    private Session googleSession(GoogleIdentity identity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/google")
                        .with(request -> {
                            request.setRemoteAddr(uniqueIp());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", google.credentialFor(identity)))))
                .andExpect(status().isOk())
                .andReturn();
        return session(result, identity.email());
    }

    private Session session(MvcResult result, String email) throws Exception {
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        String refresh = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(COOKIE_NAME + "="))
                .map(cookie -> cookie.substring(COOKIE_NAME.length() + 1, cookie.indexOf(';')))
                .findFirst().orElseThrow();
        long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        return new Session(userId, email, token, refresh);
    }

    private GoogleIdentity identity() {
        return new GoogleIdentity(UUID.randomUUID().toString(),
                "google-" + UUID.randomUUID() + "@example.com", "Maria no Google", "https://lh3.example/maria.png");
    }

    /** Uma linha em cada tabela que pertence à conta (os refresh tokens vêm do login). */
    private void seedEverything(long userId) {
        Long account = jdbcTemplate.queryForObject(
                "INSERT INTO accounts(user_id, name, type) VALUES (?, 'Conta', 'CHECKING') RETURNING id", Long.class, userId);
        Long category = jdbcTemplate.queryForObject(
                "INSERT INTO categories(user_id, name, type) VALUES (?, 'Minha categoria', 'EXPENSE') RETURNING id", Long.class, userId);
        Long transaction = jdbcTemplate.queryForObject("""
                INSERT INTO transactions(account_id, category_id, amount, type, date)
                VALUES (?, ?, 10.00, 'EXPENSE', CURRENT_DATE) RETURNING id
                """, Long.class, account, category);
        jdbcTemplate.update("INSERT INTO budgets(user_id, category_id, amount_limit, month, year) VALUES (?, ?, 100.00, 1, 2030)", userId, category);
        Long recurrence = jdbcTemplate.queryForObject("""
                INSERT INTO bill_recurrences(user_id, account_id, category_id, amount, type, day_of_month, start_date)
                VALUES (?, ?, ?, 50.00, 'PAYABLE', 5, CURRENT_DATE) RETURNING id
                """, Long.class, userId, account, category);
        jdbcTemplate.update("""
                INSERT INTO bills(user_id, account_id, category_id, amount, due_date, type, recurrence_id)
                VALUES (?, ?, ?, 50.00, CURRENT_DATE, 'PAYABLE', ?)
                """, userId, account, category, recurrence);
        jdbcTemplate.update("""
                INSERT INTO bills(user_id, account_id, category_id, amount, due_date, type, status, settled_at, settled_amount, transaction_id)
                VALUES (?, ?, ?, 10.00, CURRENT_DATE, 'PAYABLE', 'SETTLED', NOW(), 10.00, ?)
                """, userId, account, category, transaction);
        jdbcTemplate.update("INSERT INTO dismissed_alerts(user_id, alert_key, fingerprint) VALUES (?, 'overdue', 'abc')", userId);
        jdbcTemplate.update("""
                INSERT INTO support_tickets(reference, category, message, reply_to, user_id, email_sent)
                VALUES (?, 'DUVIDA', 'Oi', 'maria@example.com', ?, TRUE)
                """, UUID.randomUUID().toString().substring(0, 6), userId);

        for (String table : List.of("email_verification_tokens", "password_reset_tokens")) {
            jdbcTemplate.update("INSERT INTO " + table + "(user_id, token_hash, expires_at) VALUES (?, ?, NOW() + INTERVAL '1 hour') ON CONFLICT (user_id) DO NOTHING",
                    userId, UUID.randomUUID().toString().replace("-", "") + "0".repeat(32));
        }
        jdbcTemplate.update("INSERT INTO email_change_tokens(user_id, new_email, token_hash, expires_at) VALUES (?, ?, ?, NOW() + INTERVAL '1 hour')",
                userId, "novo-" + UUID.randomUUID() + "@example.com", UUID.randomUUID().toString().replace("-", "") + "0".repeat(32));
    }

    private int rowsOf(long userId) {
        int total = 0;
        for (String table : USER_TABLES) {
            total += count("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", userId);
        }
        return total;
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "10.50." + (n / 250) + "." + (n % 250 + 1);
    }
}
