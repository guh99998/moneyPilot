package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionFilterAndPaginationIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L;      // Alimentação, EXPENSE
    private static final Long TRANSPORT_CATEGORY_ID = 4L; // Transporte, EXPENSE
    private static final Long SALARY_CATEGORY_ID = 1L;    // Salário, INCOME

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void filterByType_returnsOnlyThatType() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);

        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", LocalDate.of(2026, 1, 5), "Mercado");
        createTransaction(token, accountId, SALARY_CATEGORY_ID, TransactionType.INCOME, "1000.00", LocalDate.of(2026, 1, 6), "Salário");

        mockMvc.perform(get("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("EXPENSE"));
    }

    @Test
    void filterByCategory_returnsOnlyThatCategory() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);

        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", LocalDate.of(2026, 1, 5), "Mercado");
        createTransaction(token, accountId, TRANSPORT_CATEGORY_ID, TransactionType.EXPENSE, "20.00", LocalDate.of(2026, 1, 6), "Uber");

        mockMvc.perform(get("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("categoryId", FOOD_CATEGORY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].categoryId").value(FOOD_CATEGORY_ID));
    }

    @Test
    void filterByAccount_returnsOnlyThatAccountsTransactions() throws Exception {
        String token = registerAndLogin();
        Long accountA = createAccount(token);
        Long accountB = createAccount(token);

        createTransaction(token, accountA, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", LocalDate.of(2026, 1, 5), "Conta A");
        createTransaction(token, accountB, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", LocalDate.of(2026, 1, 5), "Conta B");

        mockMvc.perform(get("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("accountId", accountA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].accountId").value(accountA));
    }

    @Test
    void filterByDateRange_isInclusiveOnBothEnds() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);

        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", LocalDate.of(2026, 1, 5), "Antes");
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "20.00", LocalDate.of(2026, 1, 10), "No limite inicial");
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "30.00", LocalDate.of(2026, 1, 15), "No limite final");
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "40.00", LocalDate.of(2026, 1, 20), "Depois");

        mockMvc.perform(get("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", "2026-01-10")
                        .param("to", "2026-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void filterByTypeTransfer_returnsBothLegsAndExcludesRegularTransactions() throws Exception {
        String token = registerAndLogin();
        Long accountA = createAccount(token);
        Long accountB = createAccount(token);

        createTransaction(token, accountA, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", LocalDate.of(2026, 1, 5), "Mercado");
        createTransfer(token, accountA, accountB, "50.00", LocalDate.of(2026, 1, 6));

        mockMvc.perform(get("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("type", "TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].transferGroupId").isNotEmpty())
                .andExpect(jsonPath("$.content[1].transferGroupId").isNotEmpty());
    }

    @Test
    void pagination_splitsResultsAcrossPagesCorrectly() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);

        for (int i = 0; i < 25; i++) {
            createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", LocalDate.of(2026, 1, 1).plusDays(i), "Transação " + i);
        }

        mockMvc.perform(get("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5));
    }

    @Test
    void defaultOrdering_isByDateDescending() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);

        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", LocalDate.of(2026, 1, 1), "Mais antiga");
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "20.00", LocalDate.of(2026, 1, 15), "Meio");
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "30.00", LocalDate.of(2026, 1, 30), "Mais recente");

        mockMvc.perform(get("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].date").value("2026-01-30"))
                .andExpect(jsonPath("$.content[2].date").value("2026-01-01"));
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
        AccountRequest request = new AccountRequest("Conta " + UUID.randomUUID(), AccountType.CHECKING, BigDecimal.valueOf(1000));

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Long createTransaction(String token, Long accountId, Long categoryId, TransactionType type, String amount, LocalDate date, String description) throws Exception {
        TransactionRequest request = new TransactionRequest(accountId, categoryId, new BigDecimal(amount), type, description, date);

        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void createTransfer(String token, Long fromAccountId, Long toAccountId, String amount, LocalDate date) throws Exception {
        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, new BigDecimal(amount), date, "Transferência");

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
