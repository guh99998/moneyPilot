package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionType;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers dos testes de integração da fatura do cartão (spec 016). */
public abstract class CreditCardTestSupport extends AbstractIntegrationTest {

    protected static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    protected static final Long FOOD_CATEGORY_ID = 3L;   // Alimentação, EXPENSE
    protected static final Long SALARY_CATEGORY_ID = 1L; // Salário, INCOME

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected Clock clock;

    protected final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void defaultToday() {
        fixToday(LocalDate.of(2026, 3, 15));
    }

    protected void fixToday(LocalDate today) {
        when(clock.instant()).thenReturn(today.atTime(12, 0).atZone(SAO_PAULO).toInstant());
        when(clock.getZone()).thenReturn(SAO_PAULO);
    }

    protected Long createAccount(String token, AccountType type, String initialBalance) throws Exception {
        return createAccountWith(token, type, initialBalance, "");
    }

    /** Cartão de crédito com fechamento e vencimento; o início das faturas é o ciclo de "hoje". */
    protected Long createCard(String token, int closingDay, int dueDay) throws Exception {
        return createAccountWith(token, AccountType.CREDIT_CARD, "0.00", ",\"closingDay\":" + closingDay + ",\"dueDay\":" + dueDay);
    }

    protected Long createAccountWith(String token, AccountType type, String initialBalance, String extra) throws Exception {
        String body = "{\"name\":\"Conta " + UUID.randomUUID() + "\",\"accountType\":\"" + type + "\",\"initialBalance\":" + initialBalance + extra + "}";
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    protected void purchase(String token, Long accountId, String amount, LocalDate date) throws Exception {
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, amount, date);
    }

    protected void refund(String token, Long accountId, String amount, LocalDate date) throws Exception {
        createTransaction(token, accountId, SALARY_CATEGORY_ID, TransactionType.INCOME, amount, date);
    }

    private void createTransaction(String token, Long accountId, Long categoryId, TransactionType type, String amount, LocalDate date) throws Exception {
        TransactionRequest request = new TransactionRequest(accountId, categoryId, new BigDecimal(amount), type, "Lançamento", date);
        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    protected void transfer(String token, Long from, Long to, String amount, LocalDate date) throws Exception {
        TransferRequest request = new TransferRequest(from, to, new BigDecimal(amount), date, "Pagamento da fatura");
        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    protected String invoicesJson(String token, Long accountId) throws Exception {
        return mockMvc.perform(get("/api/v1/accounts/{id}/invoices", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    protected String registerAndLogin() throws Exception {
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

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected static BigDecimal money(Object value) {
        return new BigDecimal(value.toString());
    }
}
