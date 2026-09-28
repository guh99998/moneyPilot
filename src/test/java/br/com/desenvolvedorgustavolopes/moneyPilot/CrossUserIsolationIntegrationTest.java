package br.com.desenvolvedorgustavolopes.moneyPilot;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.budget.BudgetRequest;
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
import java.time.Year;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CrossUserIsolationIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L; // "Alimentação", global, EXPENSE

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void userCannotReadUpdateOrDeleteAnotherUsersAccount() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();

        Long accountId = createAccount(tokenA);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/accounts/{id}", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequest("Conta B", AccountType.CHECKING, BigDecimal.ZERO))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/accounts/{id}", accountId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userAccountListingNeverShowsAnotherUsersAccounts() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();

        createAccount(tokenA);

        mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void userCannotReadOrDeleteAnotherUsersTransaction() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();

        Long accountId = createAccount(tokenA);
        Long transactionId = createTransaction(tokenA, accountId);

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/transactions/{id}", transactionId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userCannotReadOrDeleteAnotherUsersBudget() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();

        Long budgetId = createBudget(tokenA);

        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/budgets/{id}", budgetId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
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

    private Long createAccount(String token) throws Exception {
        AccountRequest request = new AccountRequest("Conta", AccountType.CHECKING, BigDecimal.valueOf(100));

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Long createTransaction(String token, Long accountId) throws Exception {
        TransactionRequest request = new TransactionRequest(
                accountId, FOOD_CATEGORY_ID, BigDecimal.valueOf(50), TransactionType.EXPENSE, "Mercado", LocalDate.now());

        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Long createBudget(String token) throws Exception {
        BudgetRequest request = new BudgetRequest(FOOD_CATEGORY_ID, BigDecimal.valueOf(500), 1, Year.now().getValue());

        MvcResult result = mockMvc.perform(post("/api/v1/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
