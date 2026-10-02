package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillRecurrenceMaterializeIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L; // "Alimentação", global, EXPENSE
    private static final Long SALARY_CATEGORY_ID = 1L; // "Salário", global, INCOME

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void day31_isClampedToTheLastDayOfEachMonth() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 31, LocalDate.of(2026, 1, 1), null);

        materialize(token, "2026-01").andExpect(status().isOk()).andExpect(jsonPath("$[0].dueDate").value("2026-01-31"));
        materialize(token, "2026-02").andExpect(status().isOk()).andExpect(jsonPath("$[0].dueDate").value("2026-02-28"));
        materialize(token, "2026-04").andExpect(status().isOk()).andExpect(jsonPath("$[0].dueDate").value("2026-04-30"));
    }

    @Test
    void day31_inLeapFebruary_isClampedTo29() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 31, LocalDate.of(2026, 1, 1), null);

        materialize(token, "2028-02").andExpect(status().isOk()).andExpect(jsonPath("$[0].dueDate").value("2028-02-29"));
    }

    @Test
    void dayThatExistsInTheMonth_isKept() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 1, 1), null);

        materialize(token, "2026-04").andExpect(status().isOk()).andExpect(jsonPath("$[0].dueDate").value("2026-04-10"));
    }

    @Test
    void generatedBill_copiesTheRecurrenceAndStartsOpen() throws Exception {
        String token = registerAndLogin();
        Long recurrenceId = createRecurrence(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "250.75", 5, LocalDate.of(2026, 1, 1), null);

        materialize(token, "2026-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].type").value("RECEIVABLE"))
                .andExpect(jsonPath("$[0].amount").value(250.75))
                .andExpect(jsonPath("$[0].categoryId").value(SALARY_CATEGORY_ID))
                .andExpect(jsonPath("$[0].recurrenceId").value(recurrenceId))
                .andExpect(jsonPath("$[0].installmentGroupId").doesNotExist())
                .andExpect(jsonPath("$[0].transactionId").doesNotExist());
    }

    @Test
    void callingTwiceForTheSameMonth_createsNothingTheSecondTime() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 1, 1), null);

        materialize(token, "2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        materialize(token, "2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void differentMonths_eachGetTheirOwnBill() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 1, 1), null);

        materialize(token, "2026-10").andExpect(jsonPath("$.length()").value(1));
        materialize(token, "2026-11").andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void recurrenceStartingNextMonth_producesNothingThisMonth_withoutError() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 11, 1), null);

        materialize(token, "2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        materialize(token, "2026-11").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void dueDateBeforeTheStartDateInTheSameMonth_isSkipped() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 11, 15), null);

        materialize(token, "2026-11").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        materialize(token, "2026-12").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void recurrenceAfterItsEndDate_producesNothing() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30));

        materialize(token, "2026-09").andExpect(jsonPath("$.length()").value(1));
        materialize(token, "2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deactivatedRecurrence_isNotMaterialized() throws Exception {
        String token = registerAndLogin();
        Long recurrenceId = createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 1, 1), null);

        deactivate(token, recurrenceId).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        deactivate(token, recurrenceId).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        materialize(token, "2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherUsersRecurrence_isNeverMaterialized() throws Exception {
        String token = registerAndLogin();
        String other = registerAndLogin();
        createRecurrence(other, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 1, 1), null);

        materialize(token, "2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void severalRecurrences_areMaterializedTogether() throws Exception {
        String token = registerAndLogin();
        createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 5, LocalDate.of(2026, 1, 1), null);
        createRecurrence(token, BillType.RECEIVABLE, SALARY_CATEGORY_ID, "3000.00", 28, LocalDate.of(2026, 1, 1), null);

        materialize(token, "2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        materialize(token, "2026-10").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recurrenceWithGeneratedBills_cannotBeDeleted_butWithoutBillsCan() throws Exception {
        String token = registerAndLogin();
        Long used = createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "100.00", 10, LocalDate.of(2026, 1, 1), null);
        Long unused = createRecurrence(token, BillType.PAYABLE, FOOD_CATEGORY_ID, "50.00", 10, LocalDate.of(2026, 1, 1), null);

        mockMvc.perform(post("/api/v1/bill-recurrences/{id}/deactivate", unused).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        materialize(token, "2026-10").andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/v1/bill-recurrences/{id}", used).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(delete("/api/v1/bill-recurrences/{id}", unused).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidMonth_isRejectedWith400() throws Exception {
        String token = registerAndLogin();

        materialize(token, "abc").andExpect(status().isBadRequest());
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/bills/materialize").param("month", "2026-10"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions materialize(String token, String month) throws Exception {
        return mockMvc.perform(post("/api/v1/bills/materialize")
                .param("month", month)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions deactivate(String token, Long recurrenceId) throws Exception {
        return mockMvc.perform(post("/api/v1/bill-recurrences/{id}/deactivate", recurrenceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private Long createRecurrence(String token, BillType type, Long categoryId, String amount, int dayOfMonth,
                                  LocalDate startDate, LocalDate endDate) throws Exception {
        BillRecurrenceRequest request = new BillRecurrenceRequest(null, categoryId, "Recorrência " + UUID.randomUUID(),
                new BigDecimal(amount), type, dayOfMonth, startDate, endDate);

        MvcResult result = mockMvc.perform(post("/api/v1/bill-recurrences")
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
