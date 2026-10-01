package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.BalanceResponse;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.SettleRequest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CashFlowForecastIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L;   // Alimentação, EXPENSE
    private static final Long SALARY_CATEGORY_ID = 1L; // Salário, INCOME
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // ---- cash-flow-forecast ----

    @Test
    void forecast_withNoBills_isFlatAtTheSameBalanceTheAccountsShow() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();
        Long accountA = createAccount(token, "1000.00");
        Long accountB = createAccount(token, "250.50");

        createTransaction(token, accountA, SALARY_CATEGORY_ID, TransactionType.INCOME, "500.25", TODAY.minusDays(3));
        createTransaction(token, accountA, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "120.99", TODAY.minusDays(1));
        // transação com data futura também entra no saldo atual, igual em /accounts/{id}/balance
        createTransaction(token, accountB, FOOD_CATEGORY_ID, TransactionType.EXPENSE, "10.00", TODAY.plusDays(5));
        // transferência se anula no total
        createTransfer(token, accountA, accountB, "300.00", TODAY.minusDays(2));

        BigDecimal sumOfAccounts = getBalance(token, accountA).currentBalance()
                .add(getBalance(token, accountB).currentBalance());

        CashFlowForecastResponse forecast = getForecast(token, 30);

        assertEquals(0, new BigDecimal("1619.76").compareTo(sumOfAccounts));
        assertEquals(0, sumOfAccounts.compareTo(forecast.currentBalance()));
        assertEquals(0, forecast.overdue().count());
        for (ForecastBucketResponse bucket : forecast.buckets()) {
            assertEquals(0, BigDecimal.ZERO.compareTo(bucket.net()));
            assertEquals(0, sumOfAccounts.compareTo(bucket.projectedBalance()));
        }
    }

    @Test
    void forecast_withNoAccountsAndNoBills_isZeroed() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();

        CashFlowForecastResponse forecast = getForecast(token, 30);

        assertEquals(0, BigDecimal.ZERO.compareTo(forecast.currentBalance()));
        assertEquals(5, forecast.buckets().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(forecast.buckets().get(4).projectedBalance()));
    }

    @Test
    void forecast_splitsIntoSevenDayBucketsAndKeepsThePartialLastOne() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();

        assertBucketShape(getForecast(token, 30), 30, List.of(7, 7, 7, 7, 2));
        assertBucketShape(getForecast(token, 60), 60, List.of(7, 7, 7, 7, 7, 7, 7, 7, 4));
        assertBucketShape(getForecast(token, 90), 90, List.of(7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 6));
    }

    @Test
    void forecast_withoutDays_defaultsToThirty() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();

        MvcResult result = mockMvc.perform(get("/api/v1/reports/cash-flow-forecast")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        CashFlowForecastResponse forecast = objectMapper.readValue(result.getResponse().getContentAsString(), CashFlowForecastResponse.class);
        assertEquals(TODAY.plusDays(29), forecast.to());
    }

    @Test
    void forecast_withDaysOutsideTheAllowedSet_returns400() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();

        for (String days : List.of("0", "7", "45", "365")) {
            mockMvc.perform(get("/api/v1/reports/cash-flow-forecast")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .param("days", days))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void forecast_placesEachBillInItsBucketAndAccumulatesTheBalance() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();
        createAccount(token, "1000.00");

        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", TODAY);                 // bucket 0, primeiro dia
        createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "200.00", TODAY.plusDays(6)); // bucket 0, último dia
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "30.00", TODAY.plusDays(7));      // bucket 1, primeiro dia
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.10", TODAY.plusDays(29));     // bucket 4, último dia da janela
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "999.00", TODAY.plusDays(30));    // fora da janela de 30 dias

        CashFlowForecastResponse forecast = getForecast(token, 30);
        List<ForecastBucketResponse> buckets = forecast.buckets();

        assertBucket(buckets.get(0), "50.00", "200.00", "150.00", "1150.00");
        assertBucket(buckets.get(1), "30.00", "0", "-30.00", "1120.00");
        assertBucket(buckets.get(2), "0", "0", "0", "1120.00");
        assertBucket(buckets.get(3), "0", "0", "0", "1120.00");
        assertBucket(buckets.get(4), "10.10", "0", "-10.10", "1109.90");

        // a soma dos buckets fecha com a diferença entre o saldo final e o atual
        BigDecimal sumOfNets = buckets.stream().map(ForecastBucketResponse::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, buckets.get(4).projectedBalance().subtract(forecast.currentBalance()).compareTo(sumOfNets));
    }

    @Test
    void forecast_billOverdueForThreeMonths_landsWholeInTheFirstBucket() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();
        createAccount(token, "500.00");

        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.minusMonths(3));
        createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "40.00", TODAY.minusDays(1));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "5.00", TODAY.plusDays(2));

        CashFlowForecastResponse forecast = getForecast(token, 30);

        assertEquals(2, forecast.overdue().count());
        assertEquals(0, new BigDecimal("100.00").compareTo(forecast.overdue().payable()));
        assertEquals(0, new BigDecimal("40.00").compareTo(forecast.overdue().receivable()));
        assertBucket(forecast.buckets().get(0), "105.00", "40.00", "-65.00", "435.00");
    }

    @Test
    void forecast_ignoresSettledAndCanceledBills() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");

        Long settled = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "300.00", TODAY.plusDays(1));
        Long canceled = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "400.00", TODAY.minusDays(10));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "25.00", TODAY.plusDays(1));

        settle(token, settled, accountId, "300.00");
        mockMvc.perform(post("/api/v1/bills/{id}/cancel", canceled).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        CashFlowForecastResponse forecast = getForecast(token, 30);

        // a baixa já saiu do saldo atual pelo lançamento; não pode ser descontada de novo
        assertEquals(0, new BigDecimal("700.00").compareTo(forecast.currentBalance()));
        assertEquals(0, forecast.overdue().count());
        assertBucket(forecast.buckets().get(0), "25.00", "0", "-25.00", "675.00");
    }

    @Test
    void forecast_doesNotSeeAnotherUsersAccountsOrBills() throws Exception {
        fixToday(TODAY);
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();
        createAccount(tokenA, "100.00");
        createAccount(tokenB, "9999.00");
        createBill(tokenB, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", TODAY.minusDays(5));

        CashFlowForecastResponse forecast = getForecast(tokenA, 30);

        assertEquals(0, new BigDecimal("100.00").compareTo(forecast.currentBalance()));
        assertEquals(0, forecast.overdue().count());
        assertEquals(0, new BigDecimal("100.00").compareTo(forecast.buckets().get(4).projectedBalance()));
    }

    // ---- bills-summary ----

    @Test
    void billsSummary_withNoBills_isZeroed() throws Exception {
        fixToday(TODAY);
        String token = registerAndLogin();

        BillsSummaryResponse summary = getSummary(token);

        assertEquals(0, summary.payable().count());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.payable().total()));
        assertEquals(0, summary.receivable().count());
        assertEquals(0, summary.settledThisMonth().count());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.settledThisMonth().payable()));
    }

    @Test
    void billsSummary_countsOpenOverdueAndSettledThisMonth() throws Exception {
        // a baixa grava settled_at com o relógio real, então o "hoje" do teste também é o real
        LocalDate today = LocalDate.now(SAO_PAULO);
        fixToday(today);
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");

        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", today.minusDays(3));   // vencido
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", today);                 // vence hoje: não é vencido
        createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "800.00", today.plusDays(10));
        Long settled = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", today.plusDays(1));
        Long canceled = createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "70.00", today.minusDays(1));

        // baixado por valor menor: o resumo mostra o que saiu de fato
        settle(token, settled, accountId, "95.00");
        mockMvc.perform(post("/api/v1/bills/{id}/cancel", canceled).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        BillsSummaryResponse summary = getSummary(token);

        assertEquals(2, summary.payable().count());
        assertEquals(0, new BigDecimal("150.00").compareTo(summary.payable().total()));
        assertEquals(1, summary.payable().overdueCount());
        assertEquals(0, new BigDecimal("100.00").compareTo(summary.payable().overdueTotal()));

        assertEquals(1, summary.receivable().count());
        assertEquals(0, new BigDecimal("800.00").compareTo(summary.receivable().total()));
        assertEquals(0, summary.receivable().overdueCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.receivable().overdueTotal()));

        assertEquals(1, summary.settledThisMonth().count());
        assertEquals(0, new BigDecimal("95.00").compareTo(summary.settledThisMonth().payable()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.settledThisMonth().receivable()));
    }

    @Test
    void billsSummary_leavesOutBillsSettledInAnotherMonth() throws Exception {
        LocalDate today = LocalDate.now(SAO_PAULO);
        fixToday(today);
        String token = registerAndLogin();
        Long accountId = createAccount(token, "1000.00");

        Long settled = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", today);
        settle(token, settled, accountId, "100.00");

        // um mês depois, a baixa feita hoje já não é "do mês"
        fixToday(today.plusMonths(1));

        assertEquals(0, getSummary(token).settledThisMonth().count());
    }

    // ---- helpers ----

    private void fixToday(LocalDate today) {
        Instant noon = today.atTime(12, 0).atZone(SAO_PAULO).toInstant();
        when(clock.instant()).thenReturn(noon);
        when(clock.getZone()).thenReturn(SAO_PAULO);
    }

    private void assertBucketShape(CashFlowForecastResponse forecast, int days, List<Integer> expectedSizes) {
        List<ForecastBucketResponse> buckets = forecast.buckets();

        assertEquals(TODAY, forecast.from());
        assertEquals(TODAY.plusDays(days - 1), forecast.to());
        assertEquals(expectedSizes, buckets.stream().map(ForecastBucketResponse::days).toList());
        assertEquals(TODAY, buckets.get(0).start());
        assertEquals(forecast.to(), buckets.get(buckets.size() - 1).end());
        for (int i = 1; i < buckets.size(); i++) {
            assertEquals(buckets.get(i - 1).end().plusDays(1), buckets.get(i).start());
        }
    }

    private void assertBucket(ForecastBucketResponse bucket, String payable, String receivable, String net, String projectedBalance) {
        assertEquals(0, new BigDecimal(payable).compareTo(bucket.payable()), "payable");
        assertEquals(0, new BigDecimal(receivable).compareTo(bucket.receivable()), "receivable");
        assertEquals(0, new BigDecimal(net).compareTo(bucket.net()), "net");
        assertEquals(0, new BigDecimal(projectedBalance).compareTo(bucket.projectedBalance()), "projectedBalance");
    }

    private CashFlowForecastResponse getForecast(String token, int days) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports/cash-flow-forecast")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("days", String.valueOf(days)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), CashFlowForecastResponse.class);
    }

    private BillsSummaryResponse getSummary(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports/bills-summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), BillsSummaryResponse.class);
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

    private void settle(String token, Long billId, Long accountId, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal(amount)))))
                .andExpect(status().isOk());
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
