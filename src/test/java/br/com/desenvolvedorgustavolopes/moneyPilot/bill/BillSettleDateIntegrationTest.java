package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillSettleDateIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L;   // Alimentação, EXPENSE
    private static final Long SALARY_CATEGORY_ID = 1L; // Salário, INCOME

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    void settleWithAPastDate_datesTheTransactionOnThePaymentDay() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        LocalDate due = today().minusDays(20);
        LocalDate paid = today().minusDays(10);
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", due);

        Long transactionId = settle(token, billId, accountId, "100.00", paid);

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(paid.toString()));
    }

    @Test
    void settleWithoutADate_usesTodayInSaoPaulo() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", today().minusDays(3));

        Long transactionId = settle(token, billId, accountId, "50.00", null);

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.date").value(today().toString()));
    }

    @Test
    void settleWithAFutureDate_returns400AndLeavesTheBillOpen() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", today());

        mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal("50.00"), today().plusDays(1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.settledOn").exists());

        mockMvc.perform(get("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.transactionId").value(nullValue()));
        mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void paymentAboveTheBillAmount_recordsWhatWasActuallyPaid() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", today().minusDays(10));

        // pago com atraso, com juros: R$ 100,00 viraram R$ 104,37
        Long transactionId = settle(token, billId, accountId, "104.37", today().minusDays(1));

        mockMvc.perform(get("/api/v1/bills/{id}", billId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.settledAmount").value(104.37));
        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.amount").value(104.37));
    }

    @Test
    void bulkSettleWithADate_datesEveryGeneratedTransaction() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long first = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00", today().minusDays(15));
        Long second = createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "20.00", today().minusDays(12));
        LocalDate paid = today().minusDays(5);

        mockMvc.perform(post("/api/v1/bills/settle")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkSettleRequest(List.of(first, second), accountId, paid))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].date").value(paid.toString()))
                .andExpect(jsonPath("$.content[1].date").value(paid.toString()));
    }

    @Test
    void bulkSettleWithAFutureDate_settlesNothing() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long first = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00", today());

        mockMvc.perform(post("/api/v1/bills/settle")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkSettleRequest(List.of(first), accountId, today().plusDays(2)))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/bills/{id}", first).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void transactionsGeneratedBySettlement_carryTheBillLink() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long payable = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "30.00", today().minusDays(2));
        Long receivable = createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "900.00", today().minusDays(1));
        Long paidTx = settle(token, payable, accountId, "30.00", today().minusDays(2));
        Long receivedTx = settle(token, receivable, accountId, "900.00", today().minusDays(1));
        Long manualTx = createTransaction(token, accountId, today().minusDays(3));

        MvcResult list = mockMvc.perform(get("/api/v1/transactions").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        String body = list.getResponse().getContentAsString();

        assertLink(body, paidTx, payable, "PAYABLE");
        assertLink(body, receivedTx, receivable, "RECEIVABLE");
        assertLink(body, manualTx, null, null);

        mockMvc.perform(get("/api/v1/transactions/{id}", paidTx).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.billId").value(payable))
                .andExpect(jsonPath("$.billType").value("PAYABLE"));
        mockMvc.perform(get("/api/v1/transactions/{id}", manualTx).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.billId").value(nullValue()));
    }

    // ---- helpers ----

    private void assertLink(String pageJson, Long transactionId, Long expectedBillId, String expectedType) {
        List<Object> billIds = JsonPath.read(pageJson, "$.content[?(@.id == " + transactionId + ")].billId");
        List<Object> types = JsonPath.read(pageJson, "$.content[?(@.id == " + transactionId + ")].billType");
        org.junit.jupiter.api.Assertions.assertEquals(1, billIds.size(), "transação " + transactionId + " na página");
        Object billId = billIds.get(0);
        org.junit.jupiter.api.Assertions.assertEquals(expectedBillId, billId == null ? null : ((Number) billId).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(expectedType, types.get(0));
    }

    private Long settle(String token, Long billId, Long accountId, String amount, LocalDate settledOn) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal(amount), settledOn))))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId")).longValue();
    }

    private Long createBill(String token, BillType type, Long categoryId, String amount, LocalDate dueDate) throws Exception {
        BillRequest request = new BillRequest(null, categoryId, "Título " + UUID.randomUUID(), new BigDecimal(amount), dueDate, type);
        MvcResult result = mockMvc.perform(post("/api/v1/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Long createTransaction(String token, Long accountId, LocalDate date) throws Exception {
        TransactionRequest request = new TransactionRequest(accountId, FOOD_CATEGORY_ID, new BigDecimal("12.00"), TransactionType.EXPENSE, "manual", date);
        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
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
        AccountRequest request = new AccountRequest("Conta " + UUID.randomUUID(), AccountType.CHECKING, new BigDecimal("1000.00"));
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
