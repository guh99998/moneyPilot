package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 015: o saldo das previsões é o saldo disponível, que ignora as contas desmarcadas. */
class AvailableBalanceIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void transferToInvestment_reducesCurrentBalanceOfBothForecasts() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        Long investment = createAccount(token, AccountType.INVESTMENT, "0.00");

        assertEquals(0, new BigDecimal("5000.00").compareTo(cashFlowBalance(token)));
        assertEquals(0, new BigDecimal("5000.00").compareTo(monthlyBalance(token)));

        transfer(token, checking, investment, "1000.00");

        assertEquals(0, new BigDecimal("4000.00").compareTo(cashFlowBalance(token)));
        assertEquals(0, new BigDecimal("4000.00").compareTo(monthlyBalance(token)));
    }

    @Test
    void togglingAnAccount_movesItInAndOutOfTheAvailableBalance() throws Exception {
        fixToday();
        String token = registerAndLogin();
        createAccount(token, AccountType.CHECKING, "1000.00");
        Long investment = createAccount(token, AccountType.INVESTMENT, "300.00");

        assertEquals(0, new BigDecimal("1000.00").compareTo(cashFlowBalance(token)));

        updateAccount(token, investment, AccountType.INVESTMENT, "300.00", true);
        assertEquals(0, new BigDecimal("1300.00").compareTo(cashFlowBalance(token)));

        updateAccount(token, investment, AccountType.INVESTMENT, "300.00", false);
        assertEquals(0, new BigDecimal("1000.00").compareTo(cashFlowBalance(token)));
    }

    @Test
    void creditCardCountsByDefault() throws Exception {
        fixToday();
        String token = registerAndLogin();
        createAccount(token, AccountType.CHECKING, "1000.00");
        createAccount(token, AccountType.CREDIT_CARD, "-200.00");

        assertEquals(0, new BigDecimal("800.00").compareTo(cashFlowBalance(token)));
    }

    @Test
    void allAccountsUnchecked_availableBalanceIsZero() throws Exception {
        fixToday();
        String token = registerAndLogin();
        createAccount(token, AccountType.INVESTMENT, "700.00");

        assertEquals(0, BigDecimal.ZERO.compareTo(cashFlowBalance(token)));
        assertEquals(0, BigDecimal.ZERO.compareTo(monthlyBalance(token)));
    }

    @Test
    void accountBalanceEndpoint_keepsCountingEveryAccount() throws Exception {
        fixToday();
        String token = registerAndLogin();
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        Long investment = createAccount(token, AccountType.INVESTMENT, "0.00");
        transfer(token, checking, investment, "1000.00");

        assertEquals(0, new BigDecimal("1000.00").compareTo(accountBalance(token, investment)));
        assertEquals(0, new BigDecimal("4000.00").compareTo(accountBalance(token, checking)));
    }

    // ---- helpers ----

    private void fixToday() {
        when(clock.instant()).thenReturn(TODAY.atTime(12, 0).atZone(SAO_PAULO).toInstant());
        when(clock.getZone()).thenReturn(SAO_PAULO);
    }

    private BigDecimal cashFlowBalance(String token) throws Exception {
        return currentBalance(token, "/api/v1/reports/cash-flow-forecast", "days", "30");
    }

    private BigDecimal monthlyBalance(String token) throws Exception {
        return currentBalance(token, "/api/v1/reports/monthly-forecast", "months", "3");
    }

    private BigDecimal currentBalance(String token, String path, String param, String value) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param(param, value))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(JsonPath.<Object>read(result.getResponse().getContentAsString(), "$.currentBalance").toString());
    }

    private BigDecimal accountBalance(String token, Long accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return new BigDecimal(JsonPath.<Object>read(result.getResponse().getContentAsString(), "$.currentBalance").toString());
    }

    private Long createAccount(String token, AccountType type, String initialBalance) throws Exception {
        String body = "{\"name\":\"Conta " + UUID.randomUUID() + "\",\"accountType\":\"" + type + "\",\"initialBalance\":" + initialBalance + "}";
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void updateAccount(String token, Long id, AccountType type, String initialBalance, boolean counts) throws Exception {
        String body = "{\"name\":\"Conta\",\"accountType\":\"" + type + "\",\"initialBalance\":" + initialBalance
                + ",\"countsAsAvailable\":" + counts + "}";
        mockMvc.perform(put("/api/v1/accounts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void transfer(String token, Long from, Long to, String amount) throws Exception {
        TransferRequest request = new TransferRequest(from, to, new BigDecimal(amount), TODAY, "Transferência");
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String registerAndLogin() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String password = "minha frase secreta";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Test User"))))
                .andExpect(status().isAccepted());
        markVerified(email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
