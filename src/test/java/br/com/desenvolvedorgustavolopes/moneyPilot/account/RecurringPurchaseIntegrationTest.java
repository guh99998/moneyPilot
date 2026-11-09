package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 016, regras 31 a 33, 36 e 37: compra recorrente no cartão. Cartão fecha dia 10 e vence dia 17;
 * "hoje" é 15/03/2026.
 */
class RecurringPurchaseIntegrationTest extends CreditCardTestSupport {

    @Test
    void create_onACardWithDays_returnsTheRecurrence() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        createRaw(token, body(card, "39.90", 20, "2026-01-20", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(card))
                .andExpect(jsonPath("$.amount").value(39.90))
                .andExpect(jsonPath("$.dayOfMonth").value(20))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.generatedUntil").doesNotExist());
    }

    @Test
    void create_onACardWithoutDaysOrAnotherAccountType_isBadRequestOnAccountId() throws Exception {
        String token = registerAndLogin();
        Long cardWithoutDays = createAccount(token, AccountType.CREDIT_CARD, "0.00");
        Long checking = createAccount(token, AccountType.CHECKING, "100.00");

        createRaw(token, body(cardWithoutDays, "39.90", 20, "2026-01-20", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists());
        createRaw(token, body(checking, "39.90", 20, "2026-01-20", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists());
    }

    @Test
    void create_withAnIncomeCategoryOrAnInvalidRange_isBadRequest() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        createRaw(token, "{\"accountId\":" + card + ",\"categoryId\":" + SALARY_CATEGORY_ID + ",\"description\":\"X\",\"amount\":10.00,\"dayOfMonth\":5,\"startDate\":\"2026-01-05\"}")
                .andExpect(status().isBadRequest());
        createRaw(token, body(card, "10.00", 5, "2026-01-05", "2026-01-05")).andExpect(status().isBadRequest());
        createRaw(token, body(card, "10.00", 32, "2026-01-05", null)).andExpect(status().isBadRequest());
        createRaw(token, body(card, "0.00", 5, "2026-01-05", null)).andExpect(status().isBadRequest());
    }

    @Test
    void materialize_generatesTheChargesUpToToday_andTwiceDoesNotDuplicate() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long id = create(token, body(card, "39.90", 20, "2026-01-20", null));

        materialize(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        materialize(token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        assertEquals(List.of("2026-02-20", "2026-01-20"), transactionDates(token, card));
        getRaw(token, "/api/v1/recurring-purchases/" + id)
                .andExpect(jsonPath("$.generatedUntil").value("2026-02-20"));
        mockMvc.perform(get("/api/v1/transactions").param("accountId", card.toString()).param("sort", "date,desc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].recurringPurchaseId").value(id))
                .andExpect(jsonPath("$.content[0].type").value("EXPENSE"));
    }

    @Test
    void materialize_dayThirtyOne_fallsOnTheLastDayOfShortMonths() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        create(token, body(card, "10.00", 31, "2026-01-31", null));

        materialize(token);

        assertEquals(List.of("2026-02-28", "2026-01-31"), transactionDates(token, card));
    }

    @Test
    void materialize_respectsStartAndEndDates() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        create(token, body(card, "10.00", 5, "2026-01-10", "2026-02-20")); // 05/01 antes do início; 05/03 depois do fim

        materialize(token);

        assertEquals(List.of("2026-02-05"), transactionDates(token, card));
    }

    @Test
    void materialize_aDeletedChargeDoesNotComeBack() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        create(token, body(card, "39.90", 20, "2026-01-20", null));
        materialize(token);
        Long firstCharge = firstTransactionId(token, card);

        mockMvc.perform(delete("/api/v1/transactions/{id}", firstCharge).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        materialize(token).andExpect(jsonPath("$.length()").value(0));

        assertEquals(1, transactionDates(token, card).size());
    }

    @Test
    void materialize_laterOnlyGeneratesTheNewMonths() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        create(token, body(card, "39.90", 20, "2026-01-20", null));
        materialize(token);

        fixToday(LocalDate.of(2026, 4, 25));
        materialize(token).andExpect(jsonPath("$.length()").value(2));

        assertEquals(4, transactionDates(token, card).size());
    }

    @Test
    void aGeneratedCharge_canBeEdited() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        create(token, body(card, "39.90", 20, "2026-02-20", null));
        materialize(token);
        Long charge = firstTransactionId(token, card);

        mockMvc.perform(put("/api/v1/transactions/{id}", charge).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + card + ",\"categoryId\":" + FOOD_CATEGORY_ID + ",\"amount\":45.00,\"type\":\"EXPENSE\",\"description\":\"Reajuste\",\"date\":\"2026-02-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(45.00));
    }

    @Test
    void update_appliesOnlyToTheChargesNotYetGenerated() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long id = create(token, body(card, "39.90", 20, "2026-02-20", null));
        materialize(token); // 20/02

        updateRaw(token, id, body(card, "49.90", 25, "2026-02-20", null)).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(49.90));
        fixToday(LocalDate.of(2026, 3, 28));
        materialize(token).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].amount").value(49.90))
                .andExpect(jsonPath("$[0].date").value("2026-03-25"));

        assertEquals(List.of("2026-03-25", "2026-02-20"), transactionDates(token, card));
    }

    @Test
    void changingTheDayAfterGenerating_doesNotChargeTheSameMonthTwice() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long id = create(token, body(card, "39.90", 5, "2026-03-01", null));
        materialize(token); // 05/03

        updateRaw(token, id, body(card, "39.90", 14, "2026-03-01", null)).andExpect(status().isOk());
        materialize(token).andExpect(jsonPath("$.length()").value(0));

        assertEquals(List.of("2026-03-05"), transactionDates(token, card));
    }

    @Test
    void deactivate_stopsGenerating() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long id = create(token, body(card, "39.90", 20, "2026-01-20", null));

        mockMvc.perform(post("/api/v1/recurring-purchases/{id}/deactivate", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        materialize(token).andExpect(jsonPath("$.length()").value(0));

        assertEquals(0, transactionDates(token, card).size());
    }

    @Test
    void delete_keepsTheChargesAlreadyGenerated() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long id = create(token, body(card, "39.90", 20, "2026-01-20", null));
        materialize(token);

        mockMvc.perform(delete("/api/v1/recurring-purchases/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        getRaw(token, "/api/v1/recurring-purchases/" + id).andExpect(status().isNotFound());
        assertEquals(2, transactionDates(token, card).size());
        mockMvc.perform(get("/api/v1/transactions").param("accountId", card.toString())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[0].recurringPurchaseId").doesNotExist());
    }

    @Test
    void list_returnsOnlyThePersonsRecurrences_andCanFilterByCard() throws Exception {
        String token = registerAndLogin();
        String other = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long card2 = createCard(token, 5, 12);
        create(token, body(card, "10.00", 20, "2026-01-20", null));
        create(token, body(card2, "20.00", 20, "2026-01-20", null));
        create(other, body(createCard(other, 10, 17), "30.00", 20, "2026-01-20", null));

        getRaw(token, "/api/v1/recurring-purchases").andExpect(jsonPath("$.totalElements").value(2));
        getRaw(token, "/api/v1/recurring-purchases?accountId=" + card2).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(20.00));
    }

    @Test
    void anotherPersonsRecurrenceOrCard_isNotFound() throws Exception {
        String owner = registerAndLogin();
        String intruder = registerAndLogin();
        Long card = createCard(owner, 10, 17);
        Long id = create(owner, body(card, "10.00", 20, "2026-01-20", null));

        getRaw(intruder, "/api/v1/recurring-purchases/" + id).andExpect(status().isNotFound());
        updateRaw(intruder, id, body(card, "10.00", 20, "2026-01-20", null)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/recurring-purchases/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(intruder))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/recurring-purchases/{id}/deactivate", id).header(HttpHeaders.AUTHORIZATION, bearer(intruder))).andExpect(status().isNotFound());
        createRaw(intruder, body(card, "10.00", 20, "2026-01-20", null)).andExpect(status().isNotFound());
    }

    @Test
    void chargesBeforeInvoicesFrom_doNotEnterAnyInvoice() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17); // invoicesFrom = 10/03/2026
        create(token, body(card, "39.90", 20, "2026-01-20", null));
        materialize(token);

        String invoices = invoicesJson(token, card);

        assertEquals(0, money("0.00").compareTo(money(JsonPath.read(invoices, "$[0].total"))));
    }

    // ---- helpers ----

    private String body(Long accountId, String amount, int day, String start, String end) {
        return "{\"accountId\":" + accountId + ",\"categoryId\":" + FOOD_CATEGORY_ID + ",\"description\":\"Streaming\",\"amount\":" + amount
                + ",\"dayOfMonth\":" + day + ",\"startDate\":\"" + start + "\"" + (end == null ? "" : ",\"endDate\":\"" + end + "\"") + "}";
    }

    private ResultActions createRaw(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/recurring-purchases")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updateRaw(String token, Long id, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/recurring-purchases/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions getRaw(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private Long create(String token, String json) throws Exception {
        String response = createRaw(token, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions materialize(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/recurring-purchases/materialize")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private List<String> transactionDates(String token, Long accountId) throws Exception {
        String json = mockMvc.perform(get("/api/v1/transactions").param("accountId", accountId.toString()).param("sort", "date,desc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.content[*].date");
    }

    private Long firstTransactionId(String token, Long accountId) throws Exception {
        String json = mockMvc.perform(get("/api/v1/transactions").param("accountId", accountId.toString()).param("sort", "date,asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.content[0].id")).longValue();
    }
}
