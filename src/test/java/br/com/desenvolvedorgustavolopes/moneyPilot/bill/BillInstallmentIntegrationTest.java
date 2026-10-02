package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillInstallmentIntegrationTest extends AbstractIntegrationTest {

    private static final Long SALARY_CATEGORY_ID = 1L; // "Salário", global, INCOME
    private static final Long FOOD_CATEGORY_ID = 3L; // "Alimentação", global, EXPENSE

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // ---------------------------------------------------------------- rounding

    @ParameterizedTest(name = "{0} in {1} installments -> first {2}, others {3}")
    @CsvSource({
            "100.00,   3,   33.34,  33.33",
            "10.00,    7,   1.48,   1.42",
            "0.03,     3,   0.01,   0.01",
            "0.02,     2,   0.01,   0.01",
            "0.05,     3,   0.03,   0.01",
            "1000.00,  7,   142.90, 142.85",
            "100.00,   360, 3.07,   0.27",
            // a total without decimals must be split in cents too, not in whole units
            "100,      3,   33.34,  33.33",
            "10,       7,   1.48,   1.42",
            "100.51,   3,   33.51,  33.50",
            // divides exactly: no leftover, so the first installment is not special
            "100.5,    3,   33.50,  33.50"
    })
    void split_roundsDown_putsTheLeftoverCentsInTheFirstInstallment_andSumsExactlyToTheTotal(
            String total, int installments, String expectedFirst, String expectedOthers) throws Exception {
        String token = registerAndLogin();

        List<BillResponse> plan = createPlan(token, plan(total, installments, LocalDate.of(2026, 1, 15)));

        assertEquals(installments, plan.size());
        assertEquals(0, new BigDecimal(expectedFirst).compareTo(plan.get(0).amount()), "first installment");
        for (int i = 1; i < plan.size(); i++) {
            assertEquals(0, new BigDecimal(expectedOthers).compareTo(plan.get(i).amount()), "installment " + (i + 1));
        }

        BigDecimal sum = plan.stream().map(BillResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal(total).compareTo(sum), "the installments must add up to the total to the cent");
        assertTrue(sum.scale() <= 2, "no fraction of a cent may exist: " + sum);
        plan.forEach(bill -> assertTrue(bill.amount().compareTo(new BigDecimal("0.01")) >= 0));
    }

    @Test
    void persistedInstallments_matchTheResponse_andStillSumToTheTotal() throws Exception {
        String token = registerAndLogin();
        List<BillResponse> created = createPlan(token, plan("10.00", 7, LocalDate.of(2026, 1, 15)));

        MvcResult result = mockMvc.perform(get("/api/v1/bills").param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7))
                .andReturn();

        BigDecimal sum = BigDecimal.ZERO;
        List<Object> amounts = JsonPath.read(result.getResponse().getContentAsString(), "$.content[*].amount");
        for (Object amount : amounts) {
            sum = sum.add(new BigDecimal(amount.toString()));
        }
        assertEquals(0, new BigDecimal("10.00").compareTo(sum));
        assertEquals(created.size(), amounts.size());
    }

    // ------------------------------------------------------------------- floor

    @Test
    void floor_belowOneCentPerInstallment_isRejectedWithTheMinimumInTheMessage() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/bills/installments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("0.01", 360, LocalDate.of(2026, 1, 15))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("3.60")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("360")));

        mockMvc.perform(post("/api/v1/bills/installments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson("3.59", 360, LocalDate.of(2026, 1, 15))))
                .andExpect(status().isBadRequest());

        assertEquals(0, countBills(token), "a rejected plan must create nothing");
    }

    @Test
    void floor_exactlyOneCentPerInstallment_isAccepted() throws Exception {
        String token = registerAndLogin();

        List<BillResponse> plan = createPlan(token, plan("3.60", 360, LocalDate.of(2026, 1, 15)));

        assertEquals(360, plan.size());
        plan.forEach(bill -> assertEquals(0, new BigDecimal("0.01").compareTo(bill.amount())));
    }

    // ------------------------------------------------------------- validation

    @Test
    void invalidPlans_areRejected_andCreateNothing() throws Exception {
        String token = registerAndLogin();
        LocalDate first = LocalDate.of(2026, 1, 15);

        for (String body : List.of(
                planJson("100.00", 1, first),                 // not an installment plan
                planJson("100.00", 361, first),               // above the cap
                planJson("100.005", 3, first),                // fraction of a cent
                planJson("-10.00", 3, first),
                planJson("0", 3, first))) {
            mockMvc.perform(post("/api/v1/bills/installments")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        // category type must agree with the bill type, as for a single bill
        mockMvc.perform(post("/api/v1/bills/installments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InstallmentPlanRequest(
                                null, SALARY_CATEGORY_ID, "x", new BigDecimal("100.00"), 3, first, BillType.PAYABLE))))
                .andExpect(status().isBadRequest());

        assertEquals(0, countBills(token));
    }

    @Test
    void anotherUsersAccount_isNotFound_andCreatesNothing() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();
        Long accountOfB = createAccount(tokenB);

        mockMvc.perform(post("/api/v1/bills/installments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InstallmentPlanRequest(
                                accountOfB, FOOD_CATEGORY_ID, "x", new BigDecimal("100.00"), 3,
                                LocalDate.of(2026, 1, 15), BillType.PAYABLE))))
                .andExpect(status().isNotFound());

        assertEquals(0, countBills(tokenA));
    }

    // ------------------------------------------------------------------- dates

    @Test
    void dates_day31_recoversInEveryLongMonth_becauseEachDateStartsFromTheOriginalOne() throws Exception {
        String token = registerAndLogin();

        List<BillResponse> plan = createPlan(token, plan("120.00", 12, LocalDate.of(2026, 1, 31)));

        List<LocalDate> expected = List.of(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31));
        assertEquals(expected, plan.stream().map(BillResponse::dueDate).toList());
    }

    @Test
    void dates_threeInstallmentsFrom31January_giveTheThirdOneOn31March() throws Exception {
        String token = registerAndLogin();

        List<BillResponse> plan = createPlan(token, plan("90.00", 3, LocalDate.of(2026, 1, 31)));

        assertEquals(List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
                plan.stream().map(BillResponse::dueDate).toList());
    }

    @Test
    void dates_crossingFebruaryOfALeapYear_usesThe29th() throws Exception {
        String token = registerAndLogin();

        List<BillResponse> plan = createPlan(token, plan("90.00", 3, LocalDate.of(2028, 1, 31)));

        assertEquals(List.of(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31)),
                plan.stream().map(BillResponse::dueDate).toList());
    }

    @Test
    void dates_startingOnALeapDay_landsOnTheLastDayOfFebruaryTheFollowingYear() throws Exception {
        String token = registerAndLogin();

        List<BillResponse> plan = createPlan(token, plan("130.00", 13, LocalDate.of(2028, 2, 29)));

        assertEquals(LocalDate.of(2028, 2, 29), plan.get(0).dueDate());
        assertEquals(LocalDate.of(2028, 3, 29), plan.get(1).dueDate());
        assertEquals(LocalDate.of(2029, 2, 28), plan.get(12).dueDate());
    }

    // ------------------------------------------------------------------- group

    @Test
    void installments_shareOneGroup_areNumberedInOrder_andStartOpen() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);

        List<BillResponse> plan = createPlan(token, new InstallmentPlanRequest(
                accountId, FOOD_CATEGORY_ID, "Geladeira", new BigDecimal("1200.00"), 6,
                LocalDate.of(2026, 3, 10), BillType.PAYABLE));
        List<BillResponse> other = createPlan(token, plan("100.00", 2, LocalDate.of(2026, 3, 10)));

        Set<UUID> groups = new HashSet<>();
        for (int i = 0; i < plan.size(); i++) {
            BillResponse bill = plan.get(i);
            groups.add(bill.installmentGroupId());
            assertEquals(i + 1, bill.installmentNumber());
            assertEquals(6, bill.installmentTotal());
            assertEquals(BillStatus.OPEN, bill.status());
            assertEquals(BillType.PAYABLE, bill.type());
            assertEquals(accountId, bill.accountId());
            assertEquals("Geladeira", bill.description());
            assertNull(bill.recurrenceId());
        }
        assertEquals(1, groups.size(), "all installments of a plan share one group id");
        assertNotEquals(groups.iterator().next(), other.get(0).installmentGroupId(), "two plans never share a group");
    }

    @Test
    void receivablePlans_areSupportedToo() throws Exception {
        String token = registerAndLogin();

        List<BillResponse> plan = createPlan(token, new InstallmentPlanRequest(
                null, SALARY_CATEGORY_ID, "Venda parcelada", new BigDecimal("300.00"), 3,
                LocalDate.of(2026, 5, 5), BillType.RECEIVABLE));

        assertEquals(3, plan.size());
        plan.forEach(bill -> assertEquals(BillType.RECEIVABLE, bill.type()));
    }

    // ------------------------------------------------------------ delete group

    @Test
    void deleteGroup_withOpenAndCanceledInstallments_removesTheWholeGroup_andOnlyThatGroup() throws Exception {
        String token = registerAndLogin();
        List<BillResponse> doomed = createPlan(token, plan("400.00", 4, LocalDate.of(2026, 3, 10)));
        List<BillResponse> untouched = createPlan(token, plan("90.00", 3, LocalDate.of(2026, 3, 10)));

        // a canceled installment has no transaction, so it does not block the deletion
        mockMvc.perform(post("/api/v1/bills/{id}/cancel", doomed.get(1).id()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/bills/installments/{groupId}", doomed.get(0).installmentGroupId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        for (BillResponse bill : doomed) {
            mockMvc.perform(get("/api/v1/bills/{id}", bill.id()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound());
        }
        for (BillResponse bill : untouched) {
            mockMvc.perform(get("/api/v1/bills/{id}", bill.id()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
        }
        assertEquals(3, countBills(token));
    }

    @Test
    void deleteGroup_withASettledInstallment_isConflict_namesTheInstallment_andDeletesNothing() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        List<BillResponse> plan = createPlan(token, plan("400.00", 4, LocalDate.of(2026, 3, 10)));
        BillResponse settled = plan.get(1);

        mockMvc.perform(post("/api/v1/bills/{id}/settle", settled.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleRequest(accountId, new BigDecimal("100.00")))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/bills/installments/{groupId}", settled.installmentGroupId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Installment 2 of 4")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bill " + settled.id())));

        // all-or-nothing: the open installments before and after the settled one are still there
        assertEquals(4, countBills(token));
        for (BillResponse bill : plan) {
            mockMvc.perform(get("/api/v1/bills/{id}", bill.id()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/bills/{id}", settled.id()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.status").value("SETTLED"));

        // undoing the settlement is the way out
        mockMvc.perform(post("/api/v1/bills/{id}/unsettle", settled.id()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/bills/installments/{groupId}", settled.installmentGroupId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        assertEquals(0, countBills(token));
    }

    @Test
    void deleteGroup_unknownAnotherUsersOrDeletedTwice_isNotFound_andInvalidIdIsBadRequest() throws Exception {
        String tokenA = registerAndLogin();
        String tokenB = registerAndLogin();
        List<BillResponse> plan = createPlan(tokenA, plan("90.00", 3, LocalDate.of(2026, 3, 10)));
        UUID groupId = plan.get(0).installmentGroupId();

        mockMvc.perform(delete("/api/v1/bills/installments/{groupId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound());

        // another user must not be able to tell the group exists, nor remove it
        mockMvc.perform(delete("/api/v1/bills/installments/{groupId}", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
        assertEquals(3, countBills(tokenA));

        mockMvc.perform(delete("/api/v1/bills/installments/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/bills/installments/{groupId}", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/bills/installments/{groupId}", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA)))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------- helpers

    private InstallmentPlanRequest plan(String total, int installments, LocalDate firstDueDate) {
        return new InstallmentPlanRequest(null, FOOD_CATEGORY_ID, "Parcelado", new BigDecimal(total),
                installments, firstDueDate, BillType.PAYABLE);
    }

    private String planJson(String total, int installments, LocalDate firstDueDate) throws Exception {
        return objectMapper.writeValueAsString(plan(total, installments, firstDueDate));
    }

    private List<BillResponse> createPlan(String token, InstallmentPlanRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bills/installments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return Arrays.asList(objectMapper.readValue(result.getResponse().getContentAsString(), BillResponse[].class));
    }

    private int countBills(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.totalElements");
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
