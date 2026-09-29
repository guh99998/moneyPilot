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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillBulkSettleIntegrationTest extends AbstractIntegrationTest {

    private static final Long SALARY_CATEGORY_ID = 1L; // "Salário", global, INCOME
    private static final Long FOOD_CATEGORY_ID = 3L; // "Alimentação", global, EXPENSE

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void everyBillIsSettledByItsOwnAmount_inRequestOrder_payablesAndReceivablesTogether() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");
        Long rent = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00");
        Long light = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "25.50");
        Long invoice = createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "300.00");

        MvcResult result = bulk(token, accountId, List.of(light, invoice, rent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(light))
                .andExpect(jsonPath("$[1].id").value(invoice))
                .andExpect(jsonPath("$[2].id").value(rent))
                .andExpect(jsonPath("$[*].status", Matchers.everyItem(Matchers.is("SETTLED"))))
                .andReturn();

        BillResponse[] settled = objectMapper.readValue(result.getResponse().getContentAsString(), BillResponse[].class);
        assertEquals(0, new BigDecimal("25.50").compareTo(settled[0].settledAmount()));
        assertEquals(0, new BigDecimal("300.00").compareTo(settled[1].settledAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(settled[2].settledAmount()));
        assertNotEquals(settled[0].transactionId(), settled[1].transactionId());
        assertNotEquals(settled[1].transactionId(), settled[2].transactionId());

        BalanceResponse balance = getBalance(token, accountId);
        assertEquals(3L, balance.transactionCount());
        assertEquals(0, new BigDecimal("1174.50").compareTo(balance.currentBalance())); // 1000 - 100 - 25.50 + 300
    }

    @Test
    void repeatedIds_areIgnoredSilently() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");
        Long a = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00");
        Long b = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "20.00");

        bulk(token, accountId, List.of(a, a, b, a, b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(a))
                .andExpect(jsonPath("$[1].id").value(b));

        BalanceResponse balance = getBalance(token, accountId);
        assertEquals(2L, balance.transactionCount());
        assertEquals(0, new BigDecimal("970.00").compareTo(balance.currentBalance()));
    }

    @Test
    void oneAlreadySettledBill_failsTheWholeBatch_citingItsId_andNothingElseIsSettled() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");
        Long a = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00");
        Long b = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "20.00");
        Long alreadySettled = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "30.00");
        Long d = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "40.00");
        settleAlone(token, alreadySettled, accountId, "30.00");

        // the bad id sits in the middle, so valid bills come both before and after it
        bulk(token, accountId, List.of(a, b, alreadySettled, d))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Bill " + alreadySettled + " ")));

        assertStillOpen(token, a, b, d);
        BalanceResponse balance = getBalance(token, accountId);
        assertEquals(1L, balance.transactionCount(), "only the transaction of the earlier, individual settle exists");
        assertEquals(0, new BigDecimal("970.00").compareTo(balance.currentBalance()));
    }

    @Test
    void theBadIdCanBeTheFirstOrTheLast_theOutcomeIsTheSame() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");
        Long a = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00");
        Long b = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "20.00");
        Long canceled = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "30.00");
        mockMvc.perform(post("/api/v1/bills/{id}/cancel", canceled).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        bulk(token, accountId, List.of(canceled, a, b))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Bill " + canceled + " ")));
        bulk(token, accountId, List.of(a, b, canceled))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("Bill " + canceled + " ")));

        assertStillOpen(token, a, b);
        assertEquals(0L, getBalance(token, accountId).transactionCount());
    }

    @Test
    void anotherUsersBillOrAnUnknownId_isNotFound_citingTheId_andNothingIsSettled() throws Exception {
        String token = registerAndLogin();
        String other = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");
        Long mine = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00");
        Long theirs = createBill(other, BillType.PAYABLE, FOOD_CATEGORY_ID, "99.00");
        long unknown = 999_999_999L;

        bulk(token, accountId, List.of(mine, theirs))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(Matchers.containsString(String.valueOf(theirs))));
        bulk(token, accountId, List.of(mine, unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(Matchers.containsString(String.valueOf(unknown))));

        assertStillOpen(token, mine);
        assertStillOpen(other, theirs);
        assertEquals(0L, getBalance(token, accountId).transactionCount());
    }

    @Test
    void anotherUsersAccount_isNotFound_andNothingIsSettled() throws Exception {
        String token = registerAndLogin();
        String other = registerAndLogin();
        Long foreignAccount = createAccount(other, "1000.00");
        Long mine = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00");

        bulk(token, foreignAccount, List.of(mine)).andExpect(status().isNotFound());

        assertStillOpen(token, mine);
        assertEquals(0L, getBalance(other, foreignAccount).transactionCount());
    }

    @Test
    void invalidRequests_areBadRequests() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");
        Long a = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00");

        bulk(token, accountId, List.of()).andExpect(status().isBadRequest());
        bulk(token, accountId, Collections.nCopies(201, a)).andExpect(status().isBadRequest());
        bulk(token, accountId, Arrays.asList(a, null)).andExpect(status().isBadRequest());
        bulk(token, null, List.of(a)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/bills/settle")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + accountId + "}"))
                .andExpect(status().isBadRequest());

        assertStillOpen(token, a);
    }

    @Test
    void theLimitOf200_isAccepted_and201IsNot() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");

        MvcResult plan = mockMvc.perform(post("/api/v1/bills/installments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InstallmentPlanRequest(
                                null, FOOD_CATEGORY_ID, "Financiamento", new BigDecimal("200.00"), 200,
                                LocalDate.of(2026, 1, 10), BillType.PAYABLE))))
                .andExpect(status().isCreated())
                .andReturn();
        List<Integer> rawIds = JsonPath.read(plan.getResponse().getContentAsString(), "$[*].id");
        List<Long> ids = new ArrayList<>();
        rawIds.forEach(id -> ids.add(id.longValue()));
        assertEquals(200, ids.size());

        bulk(token, accountId, ids)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(200));

        BalanceResponse balance = getBalance(token, accountId);
        assertEquals(200L, balance.transactionCount());
        assertEquals(0, new BigDecimal("800.00").compareTo(balance.currentBalance())); // 200 x 1.00
    }

    // ----------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.ResultActions bulk(String token, Long accountId, List<Long> billIds) throws Exception {
        return mockMvc.perform(post("/api/v1/bills/settle")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BulkSettleRequest(billIds, accountId))));
    }

    private void settleAlone(String token, Long billId, Long accountId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal(amount)))))
                .andExpect(status().isOk());
    }

    private void assertStillOpen(String token, Long... billIds) throws Exception {
        for (Long id : billIds) {
            mockMvc.perform(get("/api/v1/bills/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.transactionId").isEmpty());
        }
    }

    private Long createBill(String token, BillType type, Long categoryId, String amount) throws Exception {
        BillRequest request = new BillRequest(null, categoryId, "Título " + UUID.randomUUID(),
                new BigDecimal(amount), LocalDate.now().plusDays(5), type);

        MvcResult result = mockMvc.perform(post("/api/v1/bills")
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

    private Long createAccount(String token, String initialBalance) throws Exception {
        AccountRequest request = new AccountRequest("Conta " + UUID.randomUUID(), AccountType.CHECKING, new BigDecimal(initialBalance));

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
