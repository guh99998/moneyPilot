package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.CreditCardTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 028, regras 7 a 12: estorno de item da fatura. */
class TransactionRefundIntegrationTest extends CreditCardTestSupport {

    private String[] installmentIds(String token, Long card) throws Exception {
        String body = "{\"accountId\":" + card + ",\"categoryId\":" + FOOD_CATEGORY_ID + ",\"description\":\"Notebook\","
                + "\"totalAmount\":400.00,\"installments\":4,\"date\":\"2026-03-20\"}";
        String json = mockMvc.perform(post("/api/v1/transactions/installments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String[] ids = new String[4];
        for (int i = 0; i < 4; i++) ids[i] = JsonPath.<Number>read(json, "$[" + i + "].id").toString();
        return ids;
    }

    private String firstIdOf(String token, Long account) throws Exception {
        String list = mockMvc.perform(get("/api/v1/transactions").param("accountId", account.toString()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(list, "$.content[0].id").toString();
    }

    private String singlePurchase(String token, Long account, String amount) throws Exception {
        purchase(token, account, amount, LocalDate.of(2026, 3, 15));
        return firstIdOf(token, account);
    }

    private ResultActions refundOf(String token, String id, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/transactions/{id}/refund", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String read(String token, String id) throws Exception {
        return mockMvc.perform(get("/api/v1/transactions/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void ca4_refundCreatesAnIncomeLinkedToThePurchaseAndTheInvoiceSumsZero() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String id = singlePurchase(token, card, "100.00");

        String json = refundOf(token, id, "{\"date\":\"2026-03-16\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertEquals(1, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals("INCOME", JsonPath.read(json, "$[0].type"));
        assertEquals("Estorno", JsonPath.read(json, "$[0].categoryName"));
        assertEquals("Estorno: Lançamento", JsonPath.read(json, "$[0].description"));
        assertEquals("2026-03-16", JsonPath.read(json, "$[0].date"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(json, "$[0].amount"))));
        assertEquals(card.intValue(), (int) JsonPath.<Integer>read(json, "$[0].accountId"));
        assertEquals(Integer.parseInt(id), (int) JsonPath.<Integer>read(json, "$[0].refundOfTransactionId"));

        String purchase = read(token, id);
        assertEquals(JsonPath.<Integer>read(json, "$[0].id"), JsonPath.<Integer>read(purchase, "$.refundTransactionId"));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(JsonPath.read(invoicesJson(token, card), "$[0].total"))));
    }

    @Test
    void listing_marksRefundedPurchasesAndRefunds() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String id = singlePurchase(token, card, "100.00");
        refundOf(token, id, "{\"date\":\"2026-03-16\"}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/transactions").param("accountId", card.toString()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.content[?(@.type=='EXPENSE')].refundTransactionId").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.type=='INCOME')].refundOfTransactionId").isNotEmpty());
    }

    @Test
    void ca5_thisAndFollowing_usesTheLaterOfTheGivenDateAndTheInstallmentDate() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String[] ids = installmentIds(token, card); // 20/03, 20/04, 20/05, 20/06

        String json = refundOf(token, ids[1], "{\"date\":\"2026-05-01\",\"scope\":\"THIS_AND_FOLLOWING\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertEquals(3, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals("2026-05-01", JsonPath.read(json, "$[0].date")); // parcela 2 (20/04) já cobrada: data informada
        assertEquals("2026-05-20", JsonPath.read(json, "$[1].date"));
        assertEquals("2026-06-20", JsonPath.read(json, "$[2].date"));
        assertNull(JsonPath.read(read(token, ids[0]), "$.refundTransactionId"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(json, "$[0].amount"))));
    }

    @Test
    void ca6_refundingAgain_is409_andAWholePurchaseRefundsOnlyWhatIsMissing() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String[] ids = installmentIds(token, card);

        refundOf(token, ids[2], "{\"date\":\"2026-03-15\"}").andExpect(status().isCreated());
        refundOf(token, ids[2], "{\"date\":\"2026-03-15\"}").andExpect(status().isConflict());

        String json = refundOf(token, ids[0], "{\"date\":\"2026-03-15\",\"scope\":\"WHOLE_PURCHASE\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(3, JsonPath.<List<Object>>read(json, "$").size());

        refundOf(token, ids[0], "{\"date\":\"2026-03-15\",\"scope\":\"WHOLE_PURCHASE\"}").andExpect(status().isConflict());
    }

    @Test
    void ca7_deletingTheRefund_bringsThePurchaseBackToNormal() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String id = singlePurchase(token, card, "100.00");
        String json = refundOf(token, id, "{\"date\":\"2026-03-16\"}").andReturn().getResponse().getContentAsString();
        Number refundId = JsonPath.read(json, "$[0].id");

        mockMvc.perform(delete("/api/v1/transactions/{id}", refundId.longValue()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertNull(JsonPath.read(read(token, id), "$.refundTransactionId"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(invoicesJson(token, card), "$[0].total"))));
    }

    @Test
    void ca8_refundedPurchaseCannotBeDeleted_andTheRefundCannotBeEdited() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String id = singlePurchase(token, card, "100.00");
        String json = refundOf(token, id, "{\"date\":\"2026-03-16\"}").andReturn().getResponse().getContentAsString();
        Number refundId = JsonPath.read(json, "$[0].id");

        mockMvc.perform(delete("/api/v1/transactions/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/transactions/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + card + ",\"categoryId\":3,\"amount\":90.00,\"type\":\"EXPENSE\",\"description\":\"x\",\"date\":\"2026-03-15\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/transactions/{id}", refundId.longValue()).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + card + ",\"categoryId\":1,\"amount\":90.00,\"type\":\"INCOME\",\"description\":\"x\",\"date\":\"2026-03-16\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingAnInstallmentGroupWithARefund_is409() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String[] ids = installmentIds(token, card);
        String group = JsonPath.read(read(token, ids[0]), "$.installmentGroupId");
        refundOf(token, ids[3], "{\"date\":\"2026-03-15\"}").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/transactions/installments/{g}", group).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/transactions/{id}", ids[0]).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyCardExpensesThatAreNotRefundsOrTransfersAreRefundable() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long checking = createAccount(token, AccountType.CHECKING, "1000.00");
        refund(token, card, "30.00", LocalDate.of(2026, 3, 15)); // receita no cartão
        String income = firstIdOf(token, card);
        String expenseInChecking = singlePurchase(token, checking, "20.00");
        String expenseInCard = singlePurchase(token, card, "20.00");
        String refundId = JsonPath.<Number>read(refundOf(token, expenseInCard, "{\"date\":\"2026-03-15\"}")
                .andReturn().getResponse().getContentAsString(), "$[0].id").toString();

        refundOf(token, income, "{\"date\":\"2026-03-15\"}").andExpect(status().isConflict());
        refundOf(token, expenseInChecking, "{\"date\":\"2026-03-15\"}").andExpect(status().isConflict());
        refundOf(token, refundId, "{\"date\":\"2026-03-15\"}").andExpect(status().isConflict());
    }

    @Test
    void validation_missingDate_andGroupScopeOutsideAnInstallment_are400() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String id = singlePurchase(token, card, "100.00");

        refundOf(token, id, "{}").andExpect(status().isBadRequest());
        refundOf(token, id, "{\"date\":\"2026-03-15\",\"scope\":\"WHOLE_PURCHASE\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.scope").exists());
    }
}
