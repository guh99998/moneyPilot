package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.SettleRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.budget.BudgetRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertIntegrationTest extends AbstractIntegrationTest {

    private static final Long SALARY_CATEGORY_ID = 1L;    // Salário, INCOME
    private static final Long FOOD_CATEGORY_ID = 3L;      // Alimentação, EXPENSE
    private static final Long TRANSPORT_CATEGORY_ID = 4L; // Transporte, EXPENSE
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private Instant now;

    @BeforeEach
    void startClock() {
        now = Instant.parse("2026-10-28T15:00:00Z"); // 12:00 em São Paulo
        when(clock.getZone()).thenReturn(SAO_PAULO);
        when(clock.instant()).thenAnswer(invocation -> now);
    }

    // CA1

    @Test
    void dueSoon_threePayablesInTheNextSevenDays_isOneAlertWithCountTotalAndLastDueDate() throws Exception {
        String token = registerAndLogin();
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(1));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "200.00", TODAY.plusDays(3));
        createBill(token, BillType.PAYABLE, TRANSPORT_CATEGORY_ID, "150.00", TODAY.plusDays(7));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "999.00", TODAY.plusDays(8)); // fora da janela

        listAlerts(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].key").value("PAYABLE_DUE_SOON"))
                .andExpect(jsonPath("$.alerts[0].kind").value("BILLS_DUE_SOON"))
                .andExpect(jsonPath("$.alerts[0].billType").value("PAYABLE"))
                .andExpect(jsonPath("$.alerts[0].count").value(3))
                .andExpect(jsonPath("$.alerts[0].total").value(450.00))
                .andExpect(jsonPath("$.alerts[0].dueUntil").value(TODAY.plusDays(7).toString()));
    }

    @Test
    void dueSoon_receivablesAreTheirOwnAlert() throws Exception {
        String token = registerAndLogin();
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(2));
        createBill(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "800.00", TODAY.plusDays(4));

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.alerts[0].key").value("PAYABLE_DUE_SOON"))
                .andExpect(jsonPath("$.alerts[1].key").value("RECEIVABLE_DUE_SOON"))
                .andExpect(jsonPath("$.alerts[1].total").value(800.00));
    }

    // CA2

    @Test
    void overdue_settlingABillRecalculatesTheAlertAndRemovesItWhenNoneIsLeft() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long first = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.minusDays(5));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", TODAY.minusDays(1));

        listAlerts(token)
                .andExpect(jsonPath("$.alerts[0].key").value("PAYABLE_OVERDUE"))
                .andExpect(jsonPath("$.alerts[0].kind").value("BILLS_OVERDUE"))
                .andExpect(jsonPath("$.alerts[0].count").value(2))
                .andExpect(jsonPath("$.alerts[0].total").value(150.00))
                .andExpect(jsonPath("$.alerts[0].dueUntil").doesNotExist());

        settle(token, first, accountId);

        listAlerts(token)
                .andExpect(jsonPath("$.alerts[0].count").value(1))
                .andExpect(jsonPath("$.alerts[0].total").value(50.00));
    }

    @Test
    void overdue_lastOverdueBillSettled_alertDisappears() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.minusDays(2));

        settle(token, billId, accountId);

        listAlerts(token).andExpect(jsonPath("$.count").value(0));
    }

    // CA3

    @Test
    void billDueToday_isDueSoonNotOverdue_andBecomesOverdueTheNextDay() throws Exception {
        String token = registerAndLogin();
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "80.00", TODAY);

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].key").value("PAYABLE_DUE_SOON"));

        now = Instant.parse("2026-10-29T15:00:00Z"); // virou o dia em São Paulo

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].key").value("PAYABLE_OVERDUE"));
    }

    @Test
    void dayChangesAtMidnightOfSaoPaulo_notAtUtcMidnight() throws Exception {
        String token = registerAndLogin();
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "80.00", TODAY);

        now = Instant.parse("2026-10-29T01:30:00Z"); // 22:30 do dia 28 em São Paulo, já é dia 29 em UTC

        listAlerts(token).andExpect(jsonPath("$.alerts[0].key").value("PAYABLE_DUE_SOON"));
    }

    @Test
    void settledAndCanceledBills_neverEnterAnyAlert() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long settled = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(2));
        Long canceled = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.minusDays(2));

        settle(token, settled, accountId);
        mockMvc.perform(post("/api/v1/bills/{id}/cancel", canceled).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        listAlerts(token).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void manyBills_stillOneAlertPerTypeAndKind() throws Exception {
        String token = registerAndLogin();
        for (int i = 0; i < 12; i++) {
            createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00", TODAY.plusDays(i % 7));
            createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00", TODAY.minusDays(i + 1));
        }

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.alerts[0].key").value("PAYABLE_OVERDUE"))
                .andExpect(jsonPath("$.alerts[0].count").value(12))
                .andExpect(jsonPath("$.alerts[1].key").value("PAYABLE_DUE_SOON"))
                .andExpect(jsonPath("$.alerts[1].count").value(12));
    }

    // CA4

    @Test
    void budget_at85Percent_isWarning_andOver100IsExceededInsteadOfIt() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        createBudget(token, FOOD_CATEGORY_ID, "1000.00", 10, 2026);
        createExpense(token, accountId, FOOD_CATEGORY_ID, "850.00", TODAY.minusDays(3));

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].key").value("BUDGET_WARNING:" + FOOD_CATEGORY_ID + ":2026-10"))
                .andExpect(jsonPath("$.alerts[0].kind").value("BUDGET_WARNING"))
                .andExpect(jsonPath("$.alerts[0].categoryName").value("Alimentação"))
                .andExpect(jsonPath("$.alerts[0].spent").value(850.00))
                .andExpect(jsonPath("$.alerts[0].budgeted").value(1000.00))
                .andExpect(jsonPath("$.alerts[0].percentUsed").value(85))
                .andExpect(jsonPath("$.alerts[0].month").value(10))
                .andExpect(jsonPath("$.alerts[0].year").value(2026));

        createExpense(token, accountId, FOOD_CATEGORY_ID, "200.00", TODAY.minusDays(1));

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].key").value("BUDGET_EXCEEDED:" + FOOD_CATEGORY_ID + ":2026-10"))
                .andExpect(jsonPath("$.alerts[0].kind").value("BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.alerts[0].percentUsed").value(105));
    }

    @Test
    void budget_exactBoundaries() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        createBudget(token, FOOD_CATEGORY_ID, "1000.00", 10, 2026);

        createExpense(token, accountId, FOOD_CATEGORY_ID, "799.99", TODAY);
        listAlerts(token).andExpect(jsonPath("$.count").value(0));

        createExpense(token, accountId, FOOD_CATEGORY_ID, "0.01", TODAY); // exatamente 80,00%
        listAlerts(token).andExpect(jsonPath("$.alerts[0].kind").value("BUDGET_WARNING"));

        createExpense(token, accountId, FOOD_CATEGORY_ID, "200.00", TODAY); // exatamente 100,00%
        listAlerts(token)
                .andExpect(jsonPath("$.alerts[0].kind").value("BUDGET_WARNING"))
                .andExpect(jsonPath("$.alerts[0].percentUsed").value(100));

        createExpense(token, accountId, FOOD_CATEGORY_ID, "0.01", TODAY); // 100,001%
        listAlerts(token).andExpect(jsonPath("$.alerts[0].kind").value("BUDGET_EXCEEDED"));
    }

    @Test
    void budget_categoryWithoutBudgetOrOtherMonth_generatesNothing() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        createExpense(token, accountId, TRANSPORT_CATEGORY_ID, "5000.00", TODAY); // sem orçamento
        createBudget(token, FOOD_CATEGORY_ID, "100.00", 11, 2026);                  // orçamento do mês seguinte
        createExpense(token, accountId, FOOD_CATEGORY_ID, "500.00", TODAY);

        listAlerts(token).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void budget_ignoresTransfersAndExpensesOfOtherMonths() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        createBudget(token, FOOD_CATEGORY_ID, "1000.00", 10, 2026);
        createExpense(token, accountId, FOOD_CATEGORY_ID, "900.00", LocalDate.of(2026, 9, 28)); // mês anterior

        listAlerts(token).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void alerts_areOrderedOverdueDueSoonExceededWarning() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00", TODAY.plusDays(1));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00", TODAY.minusDays(1));
        createBudget(token, FOOD_CATEGORY_ID, "100.00", 10, 2026);
        createBudget(token, TRANSPORT_CATEGORY_ID, "100.00", 10, 2026);
        createExpense(token, accountId, FOOD_CATEGORY_ID, "90.00", TODAY);
        createExpense(token, accountId, TRANSPORT_CATEGORY_ID, "150.00", TODAY);

        MvcResult result = listAlerts(token).andReturn();
        List<String> kinds = JsonPath.read(result.getResponse().getContentAsString(), "$.alerts[*].kind");

        assertEquals(List.of("BILLS_OVERDUE", "BILLS_DUE_SOON", "BUDGET_EXCEEDED", "BUDGET_WARNING"), kinds);
    }

    // CA5

    @Test
    void dismissed_staysHiddenWhileNothingChanges_andComesBackWhenTheContentChanges() throws Exception {
        String token = registerAndLogin();
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(2));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", TODAY.plusDays(3));

        dismiss(token, "PAYABLE_DUE_SOON").andExpect(status().isNoContent());

        listAlerts(token).andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.alerts.length()").value(0));
        listAlerts(token).andExpect(jsonPath("$.count").value(0)); // continua escondido

        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "10.00", TODAY.plusDays(4)); // entra título novo na janela

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].count").value(3))
                .andExpect(jsonPath("$.alerts[0].total").value(160.00));
    }

    @Test
    void dismissed_comesBackWhenOnlyTheTotalChanges() throws Exception {
        String token = registerAndLogin();
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(2));
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", TODAY.plusDays(3));
        dismiss(token, "PAYABLE_DUE_SOON").andExpect(status().isNoContent());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/bills/{id}", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BillRequest(
                                null, FOOD_CATEGORY_ID, "ajustado", new BigDecimal("120.00"), TODAY.plusDays(2), BillType.PAYABLE))))
                .andExpect(status().isOk());

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].count").value(2))
                .andExpect(jsonPath("$.alerts[0].total").value(170.00));
    }

    @Test
    void dismissedBudgetWarning_staysHiddenWithMoreSpendInTheSameBand_butExceededShowsUp() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        createBudget(token, FOOD_CATEGORY_ID, "1000.00", 10, 2026);
        createExpense(token, accountId, FOOD_CATEGORY_ID, "850.00", TODAY);
        String warningKey = "BUDGET_WARNING:" + FOOD_CATEGORY_ID + ":2026-10";

        dismiss(token, warningKey).andExpect(status().isNoContent());
        createExpense(token, accountId, FOOD_CATEGORY_ID, "100.00", TODAY); // 95%, mesma faixa

        listAlerts(token).andExpect(jsonPath("$.count").value(0));

        createExpense(token, accountId, FOOD_CATEGORY_ID, "100.00", TODAY); // 105%: mudou de faixa

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].kind").value("BUDGET_EXCEEDED"));
    }

    @Test
    void dismissedBudgetAlert_comesBackInANewMonth() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        createBudget(token, FOOD_CATEGORY_ID, "1000.00", 10, 2026);
        createBudget(token, FOOD_CATEGORY_ID, "1000.00", 11, 2026);
        createExpense(token, accountId, FOOD_CATEGORY_ID, "900.00", TODAY);
        createExpense(token, accountId, FOOD_CATEGORY_ID, "900.00", LocalDate.of(2026, 11, 2));
        dismiss(token, "BUDGET_WARNING:" + FOOD_CATEGORY_ID + ":2026-10").andExpect(status().isNoContent());

        now = Instant.parse("2026-11-03T15:00:00Z");

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].key").value("BUDGET_WARNING:" + FOOD_CATEGORY_ID + ":2026-11"));
    }

    @Test
    void dismissal_isForgottenWhenTheAlertGoesAway_soTheSameNumbersLaterShowAgain() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        Long billId = createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(2));
        dismiss(token, "PAYABLE_DUE_SOON").andExpect(status().isNoContent());

        settle(token, billId, accountId);
        listAlerts(token).andExpect(jsonPath("$.count").value(0)); // alerta sumiu, a dispensa é esquecida

        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(2)); // mesmos números

        listAlerts(token).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void dismissing_twiceKeepsASingleDismissal() throws Exception {
        String token = registerAndLogin();
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(2));

        dismiss(token, "PAYABLE_DUE_SOON").andExpect(status().isNoContent());
        createBill(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "20.00", TODAY.plusDays(2));
        dismiss(token, "PAYABLE_DUE_SOON").andExpect(status().isNoContent());

        listAlerts(token).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void dismiss_withoutAnActiveAlertForTheKey_is404_andBlankKeyIs400() throws Exception {
        String token = registerAndLogin();

        dismiss(token, "PAYABLE_OVERDUE").andExpect(status().isNotFound());
        dismiss(token, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.key").exists());
    }

    // CA7

    @Test
    void userWithNothingPending_hasZeroAlerts() throws Exception {
        String token = registerAndLogin();

        listAlerts(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.alerts.length()").value(0));
    }

    // CA8

    @Test
    void alerts_neverIncludeAnotherUsersData_andDismissIsScopedToTheUser() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();
        Long accountA = createAccount(tokenA);
        createBill(tokenA, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.minusDays(3));
        createBill(tokenA, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", TODAY.plusDays(3));
        createBudget(tokenA, FOOD_CATEGORY_ID, "100.00", 10, 2026);
        createExpense(tokenA, accountA, FOOD_CATEGORY_ID, "500.00", TODAY);

        listAlerts(tokenB).andExpect(jsonPath("$.count").value(0));
        listAlerts(tokenA).andExpect(jsonPath("$.count").value(3));

        dismiss(tokenB, "PAYABLE_OVERDUE").andExpect(status().isNotFound()); // B não pode mexer no alerta de A
        listAlerts(tokenA).andExpect(jsonPath("$.count").value(3));

        dismiss(tokenA, "PAYABLE_OVERDUE").andExpect(status().isNoContent());
        listAlerts(tokenA).andExpect(jsonPath("$.count").value(2));
    }

    // 018: alertas de chamado

    @Test
    void ticketStatusChange_createsAnAlertWithTheNewStatus_andNeverChangedTicketsDoNot() throws Exception {
        String email = newEmail();
        String token = registerAndLogin(email);
        insertTicket("AAAAAA", userId(email), "OPEN", null);
        listAlerts(token).andExpect(jsonPath("$.count").value(0));

        insertTicket("BBBBBB", userId(email), "IN_PROGRESS", now);

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].key").value("SUPPORT_TICKET:BBBBBB"))
                .andExpect(jsonPath("$.alerts[0].kind").value("SUPPORT_TICKET_UPDATED"))
                .andExpect(jsonPath("$.alerts[0].ticketReference").value("BBBBBB"))
                .andExpect(jsonPath("$.alerts[0].ticketStatus").value("IN_PROGRESS"));
    }

    @Test
    void ticketAlert_dismissedStaysHidden_andComesBackWhenTheStatusChangesAgain() throws Exception {
        String email = newEmail();
        String token = registerAndLogin(email);
        insertTicket("CCCCCC", userId(email), "IN_PROGRESS", now);

        dismiss(token, "SUPPORT_TICKET:CCCCCC").andExpect(status().isNoContent());
        listAlerts(token).andExpect(jsonPath("$.count").value(0));

        jdbcTemplate.update("UPDATE support_tickets SET status = 'RESOLVED', status_changed_at = ? WHERE reference = 'CCCCCC'",
                Timestamp.from(now));

        listAlerts(token)
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.alerts[0].ticketStatus").value("RESOLVED"));
    }

    @Test
    void ticketAlert_disappearsAfterThirtyDays() throws Exception {
        String email = newEmail();
        String token = registerAndLogin(email);
        Instant limit = now.minus(30, ChronoUnit.DAYS);
        insertTicket("DDDDDD", userId(email), "RESOLVED", limit);
        listAlerts(token).andExpect(jsonPath("$.count").value(1)); // exatamente 30 dias ainda vale

        jdbcTemplate.update("UPDATE support_tickets SET status_changed_at = ? WHERE reference = 'DDDDDD'",
                Timestamp.from(limit.minusSeconds(1)));
        listAlerts(token).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void ticketAlert_isOnlyForTheOwnerOfTheTicket() throws Exception {
        String owner = newEmail();
        registerAndLogin(owner);
        String other = registerAndLogin(newEmail());
        insertTicket("EEEEEE", userId(owner), "RESOLVED", now);

        listAlerts(other).andExpect(jsonPath("$.count").value(0));
        dismiss(other, "SUPPORT_TICKET:EEEEEE").andExpect(status().isNotFound());
    }

    @Test
    void alerts_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/alerts/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"PAYABLE_OVERDUE\"}"))
                .andExpect(status().isUnauthorized());
    }

    // helpers

    private ResultActions listAlerts(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/alerts").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions dismiss(String token, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/alerts/dismiss")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("key", key))));
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

    private void settle(String token, Long billId, Long accountId) throws Exception {
        mockMvc.perform(post("/api/v1/bills/{id}/settle", billId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal("1.00")))))
                .andExpect(status().isOk());
    }

    private void createBudget(String token, Long categoryId, String amountLimit, Integer month, Integer year) throws Exception {
        BudgetRequest request = new BudgetRequest(categoryId, new BigDecimal(amountLimit), month, year);

        mockMvc.perform(post("/api/v1/budgets")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void createExpense(String token, Long accountId, Long categoryId, String amount, LocalDate date) throws Exception {
        TransactionRequest request = new TransactionRequest(accountId, categoryId, new BigDecimal(amount), TransactionType.EXPENSE, "desc", date);

        mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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

    private String newEmail() {
        return "alert-" + UUID.randomUUID() + "@example.com";
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertTicket(String reference, long userId, String status, Instant statusChangedAt) {
        jdbcTemplate.update("""
                        INSERT INTO support_tickets (reference, category, message, reply_to, user_id, status, email_sent, created_at, status_changed_at)
                        VALUES (?, 'PROBLEMA', 'Mensagem', 'pessoa@example.com', ?, ?, TRUE, ?, ?)
                        """,
                reference, userId, status, Timestamp.from(now),
                statusChangedAt == null ? null : Timestamp.from(statusChangedAt));
    }

    private String registerAndLogin() throws Exception {
        return registerAndLogin(newEmail());
    }

    private String registerAndLogin(String email) throws Exception {
        String password = "minha frase secreta";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Test User"))))
                .andExpect(status().isAccepted());
        markVerified(email);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        assertTrue(!token.isBlank());
        return token;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
