package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.CreditCardTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 016, regras 8 e 9: compra parcelada no cartão. */
class InstallmentPurchaseIntegrationTest extends CreditCardTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Test
    void create_makesOneExpensePerMonthWithTheCentOnTheFirst() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        MvcResult result = postInstallments(token, card, FOOD_CATEGORY_ID, "100.00", 3, LocalDate.of(2026, 1, 31))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();

        assertEquals(3, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals(0, new BigDecimal("33.34").compareTo(money(JsonPath.read(json, "$[0].amount"))));
        assertEquals(0, new BigDecimal("33.33").compareTo(money(JsonPath.read(json, "$[1].amount"))));
        assertEquals(0, new BigDecimal("33.33").compareTo(money(JsonPath.read(json, "$[2].amount"))));
        assertEquals("2026-01-31", JsonPath.read(json, "$[0].date"));
        assertEquals("2026-02-28", JsonPath.read(json, "$[1].date"));
        assertEquals("2026-03-31", JsonPath.read(json, "$[2].date"));
        assertEquals("EXPENSE", JsonPath.read(json, "$[0].type"));
        assertEquals(1, (int) JsonPath.<Integer>read(json, "$[0].installmentNumber"));
        assertEquals(3, (int) JsonPath.<Integer>read(json, "$[2].installmentTotal"));
        assertEquals(JsonPath.<String>read(json, "$[0].installmentGroupId"), JsonPath.read(json, "$[2].installmentGroupId"));
    }

    @Test
    void installments_fallInConsecutiveInvoices() throws Exception {
        String token = registerAndLogin();
        fixToday(LocalDate.of(2026, 1, 15));
        Long card = createCard(token, 10, 17); // invoicesFrom = 2026-01-10
        postInstallments(token, card, FOOD_CATEGORY_ID, "100.00", 3, LocalDate.of(2026, 1, 20)).andExpect(status().isCreated());
        fixToday(TODAY);

        String json = invoicesJson(token, card);

        assertEquals(3, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals(0, new BigDecimal("33.34").compareTo(money(JsonPath.read(json, "$[0].total"))));
        assertEquals(0, new BigDecimal("33.33").compareTo(money(JsonPath.read(json, "$[1].total"))));
        assertEquals(0, new BigDecimal("33.33").compareTo(money(JsonPath.read(json, "$[2].total"))));
    }

    @Test
    void installmentsBeforeInvoicesFrom_doNotEnterAnyInvoice() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17); // invoicesFrom = 2026-03-10
        // Compra em andamento lançada com a data original: só as parcelas do ciclo atual em diante entram.
        postInstallments(token, card, FOOD_CATEGORY_ID, "300.00", 4, LocalDate.of(2026, 1, 20)).andExpect(status().isCreated());

        String json = invoicesJson(token, card);

        assertEquals(0, new BigDecimal("75.00").compareTo(money(JsonPath.read(json, "$[0].total"))));
        assertEquals("2026-03-10", JsonPath.read(json, "$[0].periodStart"));
    }

    @Test
    void update_ofAnInstallment_isAllowedSinceSpec028() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String json = postInstallments(token, card, FOOD_CATEGORY_ID, "100.00", 2, TODAY)
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$[0].id");

        mockMvc.perform(put("/api/v1/transactions/{id}", id.longValue())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + card + ",\"categoryId\":3,\"amount\":10.00,\"type\":\"EXPENSE\",\"description\":\"x\",\"date\":\"2026-03-15\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void delete_ofOneInstallment_removesTheWholeGroup() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String json = postInstallments(token, card, FOOD_CATEGORY_ID, "100.00", 3, TODAY)
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$[1].id");

        mockMvc.perform(delete("/api/v1/transactions/{id}", id.longValue()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions").param("accountId", card.toString()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void deleteGroup_removesAllInstallments() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String json = postInstallments(token, card, FOOD_CATEGORY_ID, "100.00", 3, TODAY)
                .andReturn().getResponse().getContentAsString();
        String groupId = JsonPath.read(json, "$[0].installmentGroupId");

        mockMvc.perform(delete("/api/v1/transactions/installments/{groupId}", groupId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions").param("accountId", card.toString()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(delete("/api/v1/transactions/installments/{groupId}", groupId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup_ofAnotherUser_is404AndKeepsTheInstallments() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        Long card = createCard(owner, 10, 17);
        String json = postInstallments(owner, card, FOOD_CATEGORY_ID, "100.00", 2, TODAY)
                .andReturn().getResponse().getContentAsString();
        String groupId = JsonPath.read(json, "$[0].installmentGroupId");

        mockMvc.perform(delete("/api/v1/transactions/installments/{groupId}", groupId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/transactions").param("accountId", card.toString()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void create_onAccountOfAnotherUser_is404() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        Long card = createCard(owner, 10, 17);

        postInstallments(other, card, FOOD_CATEGORY_ID, "100.00", 2, TODAY).andExpect(status().isNotFound());
    }

    @Test
    void create_onAccountThatIsNotACreditCard_is400() throws Exception {
        String token = registerAndLogin();
        Long checking = createAccount(token, AccountType.CHECKING, "0.00");

        postInstallments(token, checking, FOOD_CATEGORY_ID, "100.00", 2, TODAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists());
    }

    @Test
    void create_withAnIncomeCategory_is400() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        postInstallments(token, card, SALARY_CATEGORY_ID, "100.00", 2, TODAY).andExpect(status().isBadRequest());
    }

    @Test
    void create_withInvalidInstallmentCountOrTinyAmount_is400() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        postInstallments(token, card, FOOD_CATEGORY_ID, "100.00", 1, TODAY).andExpect(status().isBadRequest());
        postInstallments(token, card, FOOD_CATEGORY_ID, "100.00", 61, TODAY).andExpect(status().isBadRequest());
        postInstallments(token, card, FOOD_CATEGORY_ID, "0.02", 3, TODAY).andExpect(status().isBadRequest());
    }

    @Test
    void create_withMoreThanTwoDecimals_is400() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        postInstallments(token, card, FOOD_CATEGORY_ID, "100.005", 3, TODAY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.totalAmount").exists());
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions postInstallments(String token, Long accountId, Long categoryId,
                                                                               String total, int installments, LocalDate date) throws Exception {
        String body = "{\"accountId\":" + accountId + ",\"categoryId\":" + categoryId + ",\"description\":\"Notebook\","
                + "\"totalAmount\":" + total + ",\"installments\":" + installments + ",\"date\":\"" + date + "\"}";
        return mockMvc.perform(post("/api/v1/transactions/installments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
