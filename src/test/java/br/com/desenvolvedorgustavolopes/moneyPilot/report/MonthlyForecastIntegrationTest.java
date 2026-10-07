package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRecurrenceRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonthlyForecastIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L;   // Alimentação, EXPENSE
    private static final Long SALARY_CATEGORY_ID = 1L; // Salário, INCOME
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void monthlyForecast_groupsOpenBillsByMonthAndAccumulatesTheBalance() throws Exception {
        fixToday();
        String token = registerAndLogin();
        createAccount(token, "1000.00");

        createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "500.00", LocalDate.of(2026, 3, 31));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "200.00", LocalDate.of(2026, 4, 1));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", LocalDate.of(2026, 4, 30));
        createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "900.00", LocalDate.of(2026, 5, 5));
        // fora da janela de 3 meses
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "999.00", LocalDate.of(2026, 6, 1));

        MonthlyForecastResponse forecast = getForecast(token, 3);

        assertEquals(LocalDate.of(2026, 3, 1), forecast.from());
        assertEquals(LocalDate.of(2026, 5, 31), forecast.to());
        assertEquals(3, forecast.months().size());
        assertBucket(forecast.months().get(0), 3, 2026, "0", "500.00", "500.00", "1500.00");
        assertBucket(forecast.months().get(1), 4, 2026, "250.00", "0", "-250.00", "1250.00");
        assertBucket(forecast.months().get(2), 5, 2026, "0", "900.00", "900.00", "2150.00");
        assertEquals(2L, forecast.months().get(1).billCount());
    }

    @Test
    void monthlyForecast_putsOverdueBillsInTheFirstMonth() throws Exception {
        fixToday();
        String token = registerAndLogin();

        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "80.00", LocalDate.of(2026, 1, 10));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "20.00", TODAY.minusDays(1));

        MonthlyForecastResponse forecast = getForecast(token, 2);

        assertEquals(2L, forecast.overdue().count());
        assertEquals(0, new BigDecimal("100.00").compareTo(forecast.overdue().payable()));
        assertBucket(forecast.months().get(0), 3, 2026, "100.00", "0", "-100.00", "-100.00");
        assertBucket(forecast.months().get(1), 4, 2026, "0", "0", "0", "-100.00");
    }

    @Test
    void monthlyForecast_projectsRecurrencesNotYetMaterializedWithoutDoubleCounting() throws Exception {
        fixToday();
        String token = registerAndLogin();
        createRecurrence(token, "100.00", 20);

        MonthlyForecastResponse before = getForecast(token, 3);
        assertBucket(before.months().get(0), 3, 2026, "100.00", "0", "-100.00", "-100.00");
        assertBucket(before.months().get(1), 4, 2026, "100.00", "0", "-100.00", "-200.00");
        assertBucket(before.months().get(2), 5, 2026, "100.00", "0", "-100.00", "-300.00");

        mockMvc.perform(post("/api/v1/bills/materialize")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("month", "2026-04"))
                .andExpect(status().isOk());

        MonthlyForecastResponse after = getForecast(token, 3);
        assertBucket(after.months().get(1), 4, 2026, "100.00", "0", "-100.00", "-200.00");
        assertEquals(1L, after.months().get(1).billCount());
        assertBucket(after.months().get(2), 5, 2026, "100.00", "0", "-100.00", "-300.00");
    }

    @Test
    void monthlyForecast_rejectsMonthsOutsideOneToTwelve() throws Exception {
        fixToday();
        String token = registerAndLogin();

        for (String months : new String[]{"0", "13"}) {
            mockMvc.perform(get("/api/v1/reports/monthly-forecast")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .param("months", months))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---- helpers ----

    private void fixToday() {
        Instant noon = TODAY.atTime(12, 0).atZone(SAO_PAULO).toInstant();
        when(clock.instant()).thenReturn(noon);
        when(clock.getZone()).thenReturn(SAO_PAULO);
    }

    private void assertBucket(MonthlyForecastBucketResponse bucket, int month, int year,
                              String payable, String receivable, String net, String projectedBalance) {
        assertEquals(month, bucket.month());
        assertEquals(year, bucket.year());
        assertEquals(0, new BigDecimal(payable).compareTo(bucket.payable()), "payable");
        assertEquals(0, new BigDecimal(receivable).compareTo(bucket.receivable()), "receivable");
        assertEquals(0, new BigDecimal(net).compareTo(bucket.net()), "net");
        assertEquals(0, new BigDecimal(projectedBalance).compareTo(bucket.projectedBalance()), "projectedBalance");
    }

    private MonthlyForecastResponse getForecast(String token, int months) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports/monthly-forecast")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("months", String.valueOf(months)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), MonthlyForecastResponse.class);
    }

    private void createBill(String token, BillType type, Long categoryId, String amount, LocalDate dueDate) throws Exception {
        BillRequest request = new BillRequest(null, categoryId, "Título " + UUID.randomUUID(), new BigDecimal(amount), dueDate, type);

        mockMvc.perform(post("/api/v1/bills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void createRecurrence(String token, String amount, int dayOfMonth) throws Exception {
        BillRecurrenceRequest request = new BillRecurrenceRequest(
                null, FOOD_CATEGORY_ID, "Aluguel", new BigDecimal(amount), BillType.PAYABLE,
                dayOfMonth, LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(post("/api/v1/bill-recurrences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void createAccount(String token, String initialBalance) throws Exception {
        AccountRequest request = new AccountRequest("Conta " + UUID.randomUUID(), AccountType.CHECKING, new BigDecimal(initialBalance));

        mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
