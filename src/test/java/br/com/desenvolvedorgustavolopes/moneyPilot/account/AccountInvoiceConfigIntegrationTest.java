package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 016, regras 1 e 2: fechamento e vencimento do cartão. */
class AccountInvoiceConfigIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void create_cardWithDays_exposesThemAndLeavesTheAvailableBalance() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"closingDay\":10,\"dueDay\":17")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closingDay").value(10))
                .andExpect(jsonPath("$.dueDay").value(17))
                .andExpect(jsonPath("$.invoicesFrom").value("2026-03-10"))
                .andExpect(jsonPath("$.countsAsAvailable").value(false));
    }

    @Test
    void create_cardWithDaysAndExplicitChoice_keepsTheChoice() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CREDIT_CARD", ",\"closingDay\":10,\"dueDay\":17,\"countsAsAvailable\":true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countsAsAvailable").value(true));
    }

    @Test
    void create_cardWithoutDays_changesNothing() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closingDay").doesNotExist())
                .andExpect(jsonPath("$.dueDay").doesNotExist())
                .andExpect(jsonPath("$.invoicesFrom").doesNotExist())
                .andExpect(jsonPath("$.countsAsAvailable").value(true));
    }

    @Test
    void create_withOnlyOneDay_is400() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"closingDay\":10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dueDay").exists());
        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"dueDay\":17")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.closingDay").exists());
    }

    @Test
    void create_daysOnAnotherAccountType_is400() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CHECKING", ",\"closingDay\":10,\"dueDay\":17")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.closingDay").exists());
    }

    @Test
    void create_dayOutOfRange_is400() throws Exception {
        fixToday();
        String token = registerAndLogin();

        for (String days : new String[]{",\"closingDay\":0,\"dueDay\":17", ",\"closingDay\":10,\"dueDay\":32"}) {
            mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", days)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void update_withoutTheDays_keepsThem() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":10,\"dueDay\":17");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"countsAsAvailable\":true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDay").value(10))
                .andExpect(jsonPath("$.dueDay").value(17))
                .andExpect(jsonPath("$.invoicesFrom").value("2026-03-10"))
                .andExpect(jsonPath("$.countsAsAvailable").value(true));
    }

    @Test
    void update_informingTheDaysForTheFirstTime_leavesTheAvailableBalanceAndSetsInvoicesFrom() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, "");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"closingDay\":20,\"dueDay\":27")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countsAsAvailable").value(false))
                .andExpect(jsonPath("$.invoicesFrom").value("2026-02-20"));
    }

    @Test
    void update_changingTheDaysLater_keepsInvoicesFromAndTheChoice() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":10,\"dueDay\":17");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"closingDay\":5,\"dueDay\":12")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDay").value(5))
                .andExpect(jsonPath("$.invoicesFrom").value("2026-03-10"))
                .andExpect(jsonPath("$.countsAsAvailable").value(false));
    }

    @Test
    void update_changingTheTypeAwayFromCard_clearsTheDays() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":10,\"dueDay\":17");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CHECKING", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDay").doesNotExist())
                .andExpect(jsonPath("$.dueDay").doesNotExist())
                .andExpect(jsonPath("$.invoicesFrom").doesNotExist());
    }

    @Test
    void update_changingTheTypeAwayFromCardWhileSendingDays_is400() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":10,\"dueDay\":17");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CHECKING", ",\"closingDay\":10,\"dueDay\":17")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_exposesTheDays() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":31,\"dueDay\":5");

        mockMvc.perform(get("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDay").value(31))
                .andExpect(jsonPath("$.dueDay").value(5));
    }

    // ---- invoicesFrom (spec 016, regra 25) ----

    @Test
    void create_withInvoicesFrom_startsAtTheCycleThatContainsTheDate() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CREDIT_CARD", ",\"closingDay\":10,\"dueDay\":17,\"invoicesFrom\":\"2026-01-20\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoicesFrom").value("2026-01-10"));
    }

    @Test
    void create_withInvoicesFromInTheFuture_is400OnTheField() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CREDIT_CARD", ",\"closingDay\":10,\"dueDay\":17,\"invoicesFrom\":\"2026-03-16\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.invoicesFrom").exists());
    }

    @Test
    void create_withInvoicesFromButNoDays_is400OnTheField() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CREDIT_CARD", ",\"invoicesFrom\":\"2026-01-20\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.invoicesFrom").exists());
    }

    @Test
    void create_withInvoicesFromOnAnotherAccountType_is400() throws Exception {
        fixToday();
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("CHECKING", ",\"invoicesFrom\":\"2026-01-20\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.invoicesFrom").exists());
    }

    @Test
    void update_withInvoicesFromOnly_usesTheDaysTheCardAlreadyHas() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":10,\"dueDay\":17"); // invoicesFrom = 2026-03-10

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"invoicesFrom\":\"2026-01-05\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingDay").value(10))
                .andExpect(jsonPath("$.invoicesFrom").value("2025-12-10"));
    }

    @Test
    void update_withInvoicesFromAndNewDays_usesTheNewDaysToFindTheCycle() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":10,\"dueDay\":17");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"closingDay\":20,\"dueDay\":27,\"invoicesFrom\":\"2026-01-25\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicesFrom").value("2026-01-20"));
    }

    @Test
    void update_movingInvoicesFromForward_isAllowed() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":10,\"dueDay\":17,\"invoicesFrom\":\"2026-01-20\"");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"invoicesFrom\":\"2026-03-12\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicesFrom").value("2026-03-10"));
    }

    @Test
    void update_withInvoicesFromOnACardWithoutDays_is400() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, "");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"invoicesFrom\":\"2026-01-20\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.invoicesFrom").exists());
    }

    @Test
    void update_withoutInvoicesFrom_keepsTheCurrentOne() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long id = createCard(token, ",\"closingDay\":10,\"dueDay\":17,\"invoicesFrom\":\"2026-01-20\"");

        mockMvc.perform(put("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", ",\"countsAsAvailable\":true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoicesFrom").value("2026-01-10"));
    }

    // ---- helpers ----

    private void fixToday() {
        when(clock.instant()).thenReturn(TODAY.atTime(12, 0).atZone(SAO_PAULO).toInstant());
        when(clock.getZone()).thenReturn(SAO_PAULO);
    }

    private String body(String type, String extra) {
        return "{\"name\":\"Cartão\",\"accountType\":\"" + type + "\",\"initialBalance\":0.00" + extra + "}";
    }

    private Long createCard(String token, String extra) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CREDIT_CARD", extra)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String registerAndLogin() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String password = "minha frase secreta";
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Test User"))))
                .andExpect(status().isAccepted());
        markVerified(email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
