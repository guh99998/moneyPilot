package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.FakeGoogleTokenVerifier;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.GoogleIdentity;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.budget.BudgetRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.RecordingEmailSender;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Specs 013 e 027: e-mail de boas-vindas, progresso dos primeiros passos e pop-up de primeiro contato. */
class OnboardingIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");
    private static final Long FOOD_CATEGORY_ID = 3L; // "Alimentação", global, EXPENSE
    private static final String WELCOME_SUBJECT = "Boas-vindas ao moneyPilot";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private FakeGoogleTokenVerifier google;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        emailSender.clear();
    }

    // O RecordingEmailSender é compartilhado com as outras classes que usam o mesmo contexto.
    @AfterEach
    void stopFailing() {
        emailSender.failFromNowOn(false);
    }

    // CA1

    @Test
    void confirmingTheVerificationLink_sendsTheWelcomeEmailOnce() throws Exception {
        String email = register();

        confirm(lastTokenSentTo(email)).andExpect(status().isNoContent());

        List<EmailMessage> welcomes = welcomesTo(email);
        assertEquals(1, welcomes.size());
        assertTrue(welcomes.get(0).text().contains("/app/"), "the e-mail links to the app");
        assertTrue(welcomes.get(0).text().contains("Maria Silva"), "the e-mail greets by name");
    }

    @Test
    void beforeVerifying_noWelcomeEmailIsSent() throws Exception {
        String email = register();

        assertEquals(0, welcomesTo(email).size());
    }

    // CA2

    @Test
    void googleCreatingANewAccount_sendsTheWelcomeEmail() throws Exception {
        GoogleIdentity identity = googleIdentity();

        signInWithGoogle(identity).andExpect(status().isOk());

        assertEquals(1, welcomesTo(identity.email()).size());
    }

    @Test
    void googleVerifyingAPendingAccount_countsAsANewAccount() throws Exception {
        String email = register();
        GoogleIdentity identity = new GoogleIdentity("sub-" + UUID.randomUUID(), email, "Maria", null);

        signInWithGoogle(identity).andExpect(status().isOk());

        assertEquals(1, welcomesTo(email).size());
    }

    @Test
    void googleLinkingAnAlreadyVerifiedAccount_sendsNothing() throws Exception {
        String email = registerVerifiedDirectly();
        GoogleIdentity identity = new GoogleIdentity("sub-" + UUID.randomUUID(), email, "Maria", null);

        signInWithGoogle(identity).andExpect(status().isOk());

        assertEquals(0, welcomesTo(email).size());
        assertEquals(0, onboardingRows(email));
    }

    // CA3

    @Test
    void confirmingAgainOrLoggingInAgain_doesNotSendAnotherWelcome() throws Exception {
        String email = register();
        String token = lastTokenSentTo(email);

        confirm(token).andExpect(status().isNoContent());
        confirm(token).andExpect(status().isNoContent());
        login(email);
        login(email);

        assertEquals(1, welcomesTo(email).size());
    }

    @Test
    void signingInWithGoogleAgain_doesNotSendAnotherWelcome() throws Exception {
        GoogleIdentity identity = googleIdentity();

        signInWithGoogle(identity).andExpect(status().isOk());
        signInWithGoogle(identity).andExpect(status().isOk());

        assertEquals(1, welcomesTo(identity.email()).size());
    }

    // CA4

    @Test
    void aNewAccount_seesTheCardWithTheThreeStepsPending() throws Exception {
        String token = newVerifiedAccountToken();

        mockMvc.perform(get("/api/v1/onboarding").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.showOnStart").value(true))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].key").value("ACCOUNT"))
                .andExpect(jsonPath("$.steps[0].done").value(false))
                .andExpect(jsonPath("$.steps[1].key").value("TRANSACTION"))
                .andExpect(jsonPath("$.steps[1].done").value(false))
                .andExpect(jsonPath("$.steps[2].key").value("BUDGET"))
                .andExpect(jsonPath("$.steps[2].done").value(false));
    }

    @Test
    void eachStepIsMarkedWhenItsThingIsCreated_andTheCardDisappearsWhenAllAreDone() throws Exception {
        String token = newVerifiedAccountToken();

        long accountId = createAccount(token);
        onboarding(token).andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.steps[0].done").value(true))
                .andExpect(jsonPath("$.steps[1].done").value(false))
                .andExpect(jsonPath("$.steps[2].done").value(false));

        createTransaction(token, accountId);
        onboarding(token).andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.steps[1].done").value(true))
                .andExpect(jsonPath("$.steps[2].done").value(false));

        createBudget(token);
        onboarding(token).andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].done").value(true))
                .andExpect(jsonPath("$.steps[1].done").value(true))
                .andExpect(jsonPath("$.steps[2].done").value(true));
    }

    // CA4: caixinha "Mostrar ao iniciar", guardada na conta

    @Test
    void turningShowOnStartOff_appliesToEveryDevice_andKeepsTheBoxAvailable() throws Exception {
        String email = register();
        confirm(lastTokenSentTo(email)).andExpect(status().isNoContent());
        String phone = login(email);
        String computer = login(email);

        savePreference(phone, false).andExpect(status().isNoContent());
        // repetir não muda nada
        savePreference(phone, false).andExpect(status().isNoContent());

        onboarding(computer).andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.showOnStart").value(false));
        onboarding(login(email)).andExpect(jsonPath("$.visible").value(false));

        savePreference(computer, true).andExpect(status().isNoContent());
        onboarding(phone).andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.showOnStart").value(true));
    }

    @Test
    void thePreferenceBody_isRequired() throws Exception {
        String token = newVerifiedAccountToken();

        mockMvc.perform(put("/api/v1/onboarding/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.showOnStart").exists());
        mockMvc.perform(put("/api/v1/onboarding/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showOnStart\":\"talvez\"}"))
                .andExpect(status().isBadRequest());

        onboarding(token).andExpect(jsonPath("$.showOnStart").value(true));
    }

    // CA6: quem dispensou o cartão da 013 (guardado direto no banco, a rota saiu)

    @Test
    void anAccountThatDismissedTheOldCard_isNotEligible_butStepsAreStillComputed() throws Exception {
        String email = register();
        confirm(lastTokenSentTo(email)).andExpect(status().isNoContent());
        String token = login(email);
        jdbcTemplate.update("UPDATE onboarding SET dismissed_at = NOW() WHERE user_id = (SELECT id FROM users WHERE email = ?)", email);
        createAccount(token);

        onboarding(token).andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].done").value(true))
                .andExpect(jsonPath("$.steps[1].done").value(false));
    }

    @Test
    void anOldAccount_isNotEligible_stepsAreComputedAndNoRowIsCreated() throws Exception {
        String email = registerVerifiedDirectly();
        String token = login(email);
        createAccount(token);

        onboarding(token).andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].done").value(true))
                .andExpect(jsonPath("$.steps[1].done").value(false))
                .andExpect(jsonPath("$.steps[2].done").value(false));
        savePreference(token, false).andExpect(status().isNoContent());

        assertEquals(0, onboardingRows(email));
        assertEquals(0, welcomesTo(email).size());
    }

    @Test
    void theOnboardingRoutesNeedALogin() throws Exception {
        mockMvc.perform(get("/api/v1/onboarding")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/onboarding/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showOnStart\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theDismissRouteIsGone() throws Exception {
        String token = newVerifiedAccountToken();

        mockMvc.perform(post("/api/v1/onboarding/dismiss").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is4xxClientError());
    }

    // CA7

    @Test
    void aStepStaysDone_evenAfterDeletingWhatCompletedIt() throws Exception {
        String token = newVerifiedAccountToken();
        long accountId = createAccount(token);
        long budgetId = createBudget(token);
        onboarding(token).andExpect(jsonPath("$.steps[0].done").value(true))
                .andExpect(jsonPath("$.steps[2].done").value(true));

        mockMvc.perform(delete("/api/v1/budgets/{id}", budgetId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/accounts/{id}", accountId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        onboarding(token).andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.steps[0].done").value(true))
                .andExpect(jsonPath("$.steps[1].done").value(false))
                .andExpect(jsonPath("$.steps[2].done").value(true));
    }

    // CA8

    @Test
    void whenTheEmailProviderFails_theVerificationAndLoginStillWork_andNothingIsResent() throws Exception {
        String email = register();
        String verificationToken = lastTokenSentTo(email);

        emailSender.failFromNowOn(true);
        confirm(verificationToken).andExpect(status().isNoContent());
        emailSender.failFromNowOn(false);

        login(email);
        confirm(verificationToken).andExpect(status().isNoContent());
        assertEquals(0, welcomesTo(email).size());
        assertEquals(1, onboardingRows(email));
    }

    // Isolamento: o cartão é sempre o da conta autenticada

    @Test
    void eachAccountSeesOnlyItsOwnCard() throws Exception {
        String first = newVerifiedAccountToken();
        String second = newVerifiedAccountToken();
        createAccount(first);

        onboarding(first).andExpect(jsonPath("$.steps[0].done").value(true));
        onboarding(second).andExpect(jsonPath("$.steps[0].done").value(false));
    }

    // Exclusão de conta (spec 012)

    @Test
    void deletingTheAccount_removesTheOnboardingRow() throws Exception {
        String email = register();
        confirm(lastTokenSentTo(email)).andExpect(status().isNoContent());
        String token = login(email);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertEquals(1, onboardingRows(email));

        mockMvc.perform(post("/api/v1/profile/deletion")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", PASSWORD))))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM onboarding WHERE user_id = ?", Integer.class, userId));
    }

    // ---- ajudantes

    private String newVerifiedAccountToken() throws Exception {
        String email = register();
        confirm(lastTokenSentTo(email)).andExpect(status().isNoContent());
        return login(email);
    }

    private String register() throws Exception {
        String email = "boas-vindas-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Maria Silva"))))
                .andExpect(status().isAccepted());
        return email;
    }

    /** Conta que existia antes das boas-vindas: verificada direto no banco, sem passar pelo link. */
    private String registerVerifiedDirectly() throws Exception {
        String email = register();
        jdbcTemplate.update("UPDATE users SET email_verified_at = NOW() WHERE email = ?", email);
        emailSender.clear();
        return email;
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token))));
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(uniqueIp());
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private GoogleIdentity googleIdentity() {
        return new GoogleIdentity("sub-" + UUID.randomUUID(), "google-" + UUID.randomUUID() + "@example.com", "Ana Souza", null);
    }

    private ResultActions signInWithGoogle(GoogleIdentity identity) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/google")
                .with(request -> {
                    request.setRemoteAddr(uniqueIp());
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("credential", google.credentialFor(identity)))));
    }

    private ResultActions onboarding(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/onboarding").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private ResultActions savePreference(String token, boolean showOnStart) throws Exception {
        return mockMvc.perform(put("/api/v1/onboarding/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("showOnStart", showOnStart))));
    }

    private List<EmailMessage> welcomesTo(String email) {
        return emailSender.sent().stream()
                .filter(message -> message.to().equals(email) && WELCOME_SUBJECT.equals(message.subject()))
                .toList();
    }

    private int onboardingRows(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM onboarding o JOIN users u ON u.id = o.user_id WHERE u.email = ?", Integer.class, email);
    }

    private String lastTokenSentTo(String email) {
        List<EmailMessage> sent = emailSender.sent().stream().filter(message -> message.to().equals(email)).toList();
        Matcher matcher = TOKEN.matcher(sent.get(sent.size() - 1).text());
        assertTrue(matcher.find(), "no link in the last e-mail to " + email);
        return matcher.group(1);
    }

    private long createAccount(String token) throws Exception {
        return idOf(post("/api/v1/accounts"), token,
                new AccountRequest("Conta", AccountType.CHECKING, BigDecimal.valueOf(100)));
    }

    private long createTransaction(String token, long accountId) throws Exception {
        return idOf(post("/api/v1/transactions"), token,
                new TransactionRequest(accountId, FOOD_CATEGORY_ID, BigDecimal.valueOf(50), TransactionType.EXPENSE, "Mercado", LocalDate.now()));
    }

    private long createBudget(String token) throws Exception {
        return idOf(post("/api/v1/budgets"), token,
                new BudgetRequest(FOOD_CATEGORY_ID, BigDecimal.valueOf(500), 1, Year.now().getValue()));
    }

    private long idOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String token, Object body) throws Exception {
        MvcResult result = mockMvc.perform(builder
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "10.40." + (n / 250) + "." + (n % 250 + 1);
    }
}
