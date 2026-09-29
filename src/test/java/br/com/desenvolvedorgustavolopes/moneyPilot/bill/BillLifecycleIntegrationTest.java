package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.BalanceResponse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final Long SALARY_CATEGORY_ID = 1L; // "Salário", global, INCOME
    private static final Long FOOD_CATEGORY_ID = 3L; // "Alimentação", global, EXPENSE

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void settlingPayable_createsExpenseForPaidAmount_andUnsettleRestoresEverything() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("1000.00"));
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("100.00"), LocalDate.now().plusDays(5));

        // paid amount differs from the bill amount on purpose: the transaction must use what was paid
        MvcResult settled = mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal("95.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.settledAmount").value(95.00))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.settledAt").isNotEmpty())
                .andReturn();

        Long transactionId = ((Number) JsonPath.read(settled.getResponse().getContentAsString(), "$.transactionId")).longValue();

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(95.00))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.categoryId").value(FOOD_CATEGORY_ID));

        BalanceResponse afterSettle = getBalance(token, accountId);
        assertEquals(0, new BigDecimal("905.00").compareTo(afterSettle.currentBalance()));
        assertEquals(1L, afterSettle.transactionCount());

        mockMvc.perform(post("/api/v1/bills/{id}/unsettle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.settledAt").isEmpty())
                .andExpect(jsonPath("$.settledAmount").isEmpty())
                .andExpect(jsonPath("$.transactionId").isEmpty());

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        BalanceResponse afterUnsettle = getBalance(token, accountId);
        assertEquals(0, new BigDecimal("1000.00").compareTo(afterUnsettle.currentBalance()));
        assertEquals(0L, afterUnsettle.transactionCount());
    }

    @Test
    void settlingReceivable_createsIncomeAndRaisesBalance() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("200.00"));
        Long billId = createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, new BigDecimal("500.00"), LocalDate.now().plusDays(3));

        MvcResult settled = mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal("500.00")))))
                .andExpect(status().isOk())
                .andReturn();

        Long transactionId = ((Number) JsonPath.read(settled.getResponse().getContentAsString(), "$.transactionId")).longValue();

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.amount").value(500.00));

        assertEquals(0, new BigDecimal("700.00").compareTo(getBalance(token, accountId).currentBalance()));
    }

    @Test
    void illegalTransitions_returnConflictAndCreateNoExtraTransaction() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("1000.00"));
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("100.00"), LocalDate.now().plusDays(5));

        // unsettle on an OPEN bill
        mockMvc.perform(post("/api/v1/bills/{id}/unsettle", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        settle(token, billId, accountId, "100.00", status().isOk());

        // second settle, and every other mutation on a SETTLED bill
        settle(token, billId, accountId, "100.00", status().isConflict());

        mockMvc.perform(post("/api/v1/bills/{id}/cancel", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/bills/{id}", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billRequest(BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("50.00"), LocalDate.now()))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        // the failed attempts must not leave a second transaction behind
        assertEquals(1L, getBalance(token, accountId).transactionCount());
        assertEquals(0, new BigDecimal("900.00").compareTo(getBalance(token, accountId).currentBalance()));
    }

    @Test
    void canceledBill_cannotBeSettledUpdatedOrDeleted() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("1000.00"));
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("100.00"), LocalDate.now().plusDays(5));

        mockMvc.perform(post("/api/v1/bills/{id}/cancel", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        settle(token, billId, accountId, "100.00", status().isConflict());

        mockMvc.perform(post("/api/v1/bills/{id}/cancel", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/bills/{id}", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billRequest(BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("50.00"), LocalDate.now()))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        assertEquals(0L, getBalance(token, accountId).transactionCount());
    }

    @Test
    void categoryTypeMustMatchBillType() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billRequest(BillType.PAYABLE, SALARY_CATEGORY_ID, new BigDecimal("10.00"), LocalDate.now()))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billRequest(BillType.RECEIVABLE, FOOD_CATEGORY_ID, new BigDecimal("10.00"), LocalDate.now()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userCannotReachAnotherUsersBills() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();
        Long accountB = createAccount(tokenB, new BigDecimal("1000.00"));
        Long billId = createBill(tokenA, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("100.00"), LocalDate.now().plusDays(5));

        mockMvc.perform(get("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/bills/{id}", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billRequest(BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("1.00"), LocalDate.now()))))
                .andExpect(status().isNotFound());

        settle(tokenB, billId, accountB, "100.00", status().isNotFound());

        mockMvc.perform(post("/api/v1/bills/{id}/unsettle", billId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/bills/{id}/cancel", billId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // and the bill is untouched for its owner
        mockMvc.perform(get("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void userCannotSettleWithAnotherUsersAccount() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();
        Long accountB = createAccount(tokenB, new BigDecimal("1000.00"));
        Long billId = createBill(tokenA, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("100.00"), LocalDate.now().plusDays(5));

        settle(tokenA, billId, accountB, "100.00", status().isNotFound());

        assertEquals(0L, getBalance(tokenB, accountB).transactionCount());
        mockMvc.perform(get("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void statusFilter_overdueMeansOpenAndDueStrictlyBeforeToday() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("1000.00"));

        Long pastId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("10.00"), LocalDate.now().minusDays(3));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("10.00"), LocalDate.now());
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("10.00"), LocalDate.now().plusDays(3));
        Long pastSettledId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("10.00"), LocalDate.now().minusDays(10));
        settle(token, pastSettledId, accountId, "10.00", status().isOk());

        mockMvc.perform(get("/api/v1/bills").param("status", "OVERDUE").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pastId));

        mockMvc.perform(get("/api/v1/bills").param("status", "OPEN").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/v1/bills").param("status", "SETTLED").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pastSettledId));

        mockMvc.perform(get("/api/v1/bills").param("status", "CANCELED").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void defaultOrderingIsDueDateAscending() throws Exception {
        String token = registerAndLogin();
        Long later = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("10.00"), LocalDate.now().plusDays(20));
        Long sooner = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, new BigDecimal("10.00"), LocalDate.now().plusDays(2));

        mockMvc.perform(get("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].id").value(sooner))
                .andExpect(jsonPath("$.content[1].id").value(later));
    }

    private void settle(String token, Long billId, Long accountId, String amount,
                        org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal(amount)))))
                .andExpect(expected);
    }

    private BillRequest billRequest(BillType type, Long categoryId, BigDecimal amount, LocalDate dueDate) {
        return new BillRequest(null, categoryId, "Título " + UUID.randomUUID(), amount, dueDate, type);
    }

    private Long createBill(String token, BillType type, Long categoryId, BigDecimal amount, LocalDate dueDate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(billRequest(type, categoryId, amount, dueDate))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
