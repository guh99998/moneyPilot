package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.budget.BudgetRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionType;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransferRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L;      // Alimentação, EXPENSE
    private static final Long TRANSPORT_CATEGORY_ID = 4L; // Transporte, EXPENSE
    private static final Long SALARY_CATEGORY_ID = 1L;    // Salário, INCOME

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // ---- monthly-summary ----

    @Test
    void monthlySummary_withNoTransactions_returnsZeroed() throws Exception {
        String token = registerAndLogin();

        MvcResult result = mockMvc.perform(get("/api/v1/reports/monthly-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("month", "1")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andReturn();

        MonthlySummaryResponse summary = objectMapper.readValue(result.getResponse().getContentAsString(), MonthlySummaryResponse.class);

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalIncome()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalExpense()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.balance()));
    }

    @Test
    void monthlySummary_sumsIncomeAndExpenseOnlyForRequestedMonth() throws Exception {
        String token = registerAndLogin();
        Long accountA = createAccount(token, new BigDecimal("0"));
        Long accountB = createAccount(token, new BigDecimal("0"));

        createTransaction(token, accountA, SALARY_CATEGORY_ID, TransactionType.INCOME, "1000.00", LocalDate.of(2026, 1, 10));
        createTransaction(token, accountA, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "300.00", LocalDate.of(2026, 1, 15));
        // fora do mês pedido: não deve contar
        createTransaction(token, accountA, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "999.00", LocalDate.of(2026, 2, 1));
        // transferência: não é receita nem despesa real, não deve contar
        createTransfer(token, accountA, accountB, "50.00", LocalDate.of(2026, 1, 20));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/monthly-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("month", "1")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andReturn();

        MonthlySummaryResponse summary = objectMapper.readValue(result.getResponse().getContentAsString(), MonthlySummaryResponse.class);

        assertEquals(0, new BigDecimal("1000.00").compareTo(summary.totalIncome()));
        assertEquals(0, new BigDecimal("300.00").compareTo(summary.totalExpense()));
        assertEquals(0, new BigDecimal("700.00").compareTo(summary.balance()));
    }

    // ---- spending-by-category ----

    @Test
    void spendingByCategory_withNoExpenses_returnsEmptyList() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/reports/spending-by-category")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("month", "1")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void spendingByCategory_groupsByCategoryAndOrdersByTotalDescending() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("0"));

        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "30.00", LocalDate.of(2026, 1, 5));
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "50.00", LocalDate.of(2026, 1, 6));
        createTransaction(token, accountId, TRANSPORT_CATEGORY_ID, TransactionType.EXPENSE, "100.00", LocalDate.of(2026, 1, 7));
        // receita não é gasto, não deve entrar
        createTransaction(token, accountId, SALARY_CATEGORY_ID, TransactionType.INCOME, "2000.00", LocalDate.of(2026, 1, 8));

        mockMvc.perform(get("/api/v1/reports/spending-by-category")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("month", "1")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoryId").value(TRANSPORT_CATEGORY_ID))
                .andExpect(jsonPath("$[0].total").value(100.00))
                .andExpect(jsonPath("$[1].categoryId").value(FOOD_CATEGORY_ID))
                .andExpect(jsonPath("$[1].total").value(80.00));
    }

    // ---- budget-vs-actual ----

    @Test
    void budgetVsActual_calculatesRemainingFromBudgetAndSpending() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("0"));

        createBudget(token, FOOD_CATEGORY_ID, "500.00", 1, 2026);
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "120.00", LocalDate.of(2026, 1, 5));
        createTransaction(token, accountId, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "80.00", LocalDate.of(2026, 1, 10));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/budget-vs-actual")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("month", "1")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();

        BudgetVsActualResponse[] response = objectMapper.readValue(result.getResponse().getContentAsString(), BudgetVsActualResponse[].class);

        assertEquals(0, new BigDecimal("500.00").compareTo(response[0].budgetedAmount()));
        assertEquals(0, new BigDecimal("200.00").compareTo(response[0].spentAmount()));
        assertEquals(0, new BigDecimal("300.00").compareTo(response[0].remainingAmount()));
    }

    @Test
    void budgetVsActual_withNoSpending_showsFullBudgetAsRemaining() throws Exception {
        String token = registerAndLogin();

        createBudget(token, FOOD_CATEGORY_ID, "500.00", 1, 2026);

        MvcResult result = mockMvc.perform(get("/api/v1/reports/budget-vs-actual")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("month", "1")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andReturn();

        BudgetVsActualResponse[] response = objectMapper.readValue(result.getResponse().getContentAsString(), BudgetVsActualResponse[].class);

        assertEquals(0, BigDecimal.ZERO.compareTo(response[0].spentAmount()));
        assertEquals(0, new BigDecimal("500.00").compareTo(response[0].remainingAmount()));
    }

    // ---- helpers ----

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

    private void createTransaction(String token, Long accountId, Long categoryId, TransactionType type, String amount, LocalDate date) throws Exception {
        TransactionRequest request = new TransactionRequest(accountId, categoryId, new BigDecimal(amount), type, "desc", date);

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void createTransfer(String token, Long fromAccountId, Long toAccountId, String amount, LocalDate date) throws Exception {
        TransferRequest request = new TransferRequest(fromAccountId, toAccountId, new BigDecimal(amount), date, "Transferência");

        mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void createBudget(String token, Long categoryId, String amountLimit, Integer month, Integer year) throws Exception {
        BudgetRequest request = new BudgetRequest(categoryId, new BigDecimal(amountLimit), month, year);

        mockMvc.perform(post("/api/v1/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
