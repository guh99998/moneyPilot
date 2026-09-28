package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountBalanceIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L;   // Alimentação, EXPENSE
    private static final Long SALARY_CATEGORY_ID = 1L; // Salário, INCOME

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void balanceWithNoTransactions_equalsInitialBalance() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("100.00"));

        BalanceResponse balance = getBalance(token, accountId);

        assertEquals(0, new BigDecimal("100.00").compareTo(balance.currentBalance()));
        assertEquals(0L, balance.transactionCount());
    }

    @Test
    void balanceWithIncomeAndExpense_addsIncomeAndSubtractsExpense() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("100.00"));

        createTransaction(token, accountId, SALARY_CATEGORY_ID, TransactionType.INCOME, new BigDecimal("50.00"));
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, new BigDecimal("30.00"));

        BalanceResponse balance = getBalance(token, accountId);

        // 100.00 + 50.00 - 30.00 = 120.00
        assertEquals(0, new BigDecimal("120.00").compareTo(balance.currentBalance()));
        assertEquals(2L, balance.transactionCount());
    }

    @Test
    void balanceWithFractionalAmounts_isExactToTheCent() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("10.25"));

        createTransaction(token, accountId, SALARY_CATEGORY_ID, TransactionType.INCOME, new BigDecimal("5.10"));
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, new BigDecimal("3.33"));

        BalanceResponse balance = getBalance(token, accountId);

        // 10.25 + 5.10 - 3.33 = 12.02
        assertEquals(0, new BigDecimal("12.02").compareTo(balance.currentBalance()));
    }

    private BalanceResponse getBalance(String token, Long accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), BalanceResponse.class);
    }

    private String registerAndLogin() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String password = "senha12345";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Test User"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private Long createAccount(String token, BigDecimal initialBalance) throws Exception {
        AccountRequest request = new AccountRequest("Conta " + UUID.randomUUID(), AccountType.CHECKING, initialBalance);

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void createTransaction(String token, Long accountId, Long categoryId, TransactionType type, BigDecimal amount) throws Exception {
        TransactionRequest request = new TransactionRequest(accountId, categoryId, amount, type, "desc", LocalDate.of(2026, 1, 1));

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
