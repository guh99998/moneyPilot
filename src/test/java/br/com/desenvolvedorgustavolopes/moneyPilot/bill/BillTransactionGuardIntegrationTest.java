package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.BalanceResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillTransactionGuardIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L; // "Alimentação", global, EXPENSE

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void deletingTheTransactionOfASettledBill_isRefusedAndNothingChanges() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("1000.00"));
        Long billId = createBill(token, new BigDecimal("100.00"));
        Long transactionId = settle(token, billId, accountId, "100.00");

        mockMvc.perform(delete("/api/v1/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("unsettle")));

        // the transaction, the link and the balance survive the refused delete
        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.transactionId").value(transactionId));

        assertEquals(0, new BigDecimal("900.00").compareTo(getBalance(token, accountId).currentBalance()));
    }

    @Test
    void updatingTheTransactionOfASettledBill_isRefusedAndTheAmountIsUntouched() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("1000.00"));
        Long billId = createBill(token, new BigDecimal("100.00"));
        Long transactionId = settle(token, billId, accountId, "100.00");

        TransactionRequest edit = new TransactionRequest(accountId, FOOD_CATEGORY_ID, new BigDecimal("1.00"),
                TransactionType.EXPENSE, "edited", LocalDate.now());

        mockMvc.perform(put("/api/v1/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.amount").value(100.00));

        assertEquals(0, new BigDecimal("900.00").compareTo(getBalance(token, accountId).currentBalance()));
    }

    @Test
    void unsettle_stillRemovesTheTransaction_andTheGuardStopsApplyingAfterwards() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("1000.00"));
        Long billId = createBill(token, new BigDecimal("100.00"));
        Long transactionId = settle(token, billId, accountId, "100.00");

        mockMvc.perform(post("/api/v1/bills/{id}/unsettle", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        assertEquals(0, new BigDecimal("1000.00").compareTo(getBalance(token, accountId).currentBalance()));

        // settling again produces a new transaction that is protected again
        Long secondTransactionId = settle(token, billId, accountId, "100.00");
        mockMvc.perform(delete("/api/v1/transactions/{id}", secondTransactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void ordinaryTransactions_andOnesOfOtherBills_remainFreelyEditableAndDeletable() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, new BigDecimal("1000.00"));

        // a settled bill exists, to prove the guard matches by transaction id and not by account or user
        Long billId = createBill(token, new BigDecimal("100.00"));
        settle(token, billId, accountId, "100.00");

        Long ordinaryId = createTransaction(token, accountId, new BigDecimal("20.00"));

        TransactionRequest edit = new TransactionRequest(accountId, FOOD_CATEGORY_ID, new BigDecimal("30.00"),
                TransactionType.EXPENSE, "edited", LocalDate.now());

        mockMvc.perform(put("/api/v1/transactions/{id}", ordinaryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30.00));

        mockMvc.perform(delete("/api/v1/transactions/{id}", ordinaryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // only the settled bill's transaction is left
        assertEquals(1L, getBalance(token, accountId).transactionCount());
        assertEquals(0, new BigDecimal("900.00").compareTo(getBalance(token, accountId).currentBalance()));
    }

    private Long settle(String token, Long billId, Long accountId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal(amount)))))
                .andExpect(status().isOk())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId")).longValue();
    }

    private Long createBill(String token, BigDecimal amount) throws Exception {
        BillRequest request = new BillRequest(null, FOOD_CATEGORY_ID, "Título " + UUID.randomUUID(), amount,
                LocalDate.now().plusDays(5), BillType.PAYABLE);

        MvcResult result = mockMvc.perform(post("/api/v1/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Long createTransaction(String token, Long accountId, BigDecimal amount) throws Exception {
        TransactionRequest request = new TransactionRequest(accountId, FOOD_CATEGORY_ID, amount,
                TransactionType.EXPENSE, "ordinary", LocalDate.now());

        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
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
