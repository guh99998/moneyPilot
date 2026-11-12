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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 028, regras 1 a 6: edição de parcela e de item da fatura. */
class TransactionEditInstallmentIntegrationTest extends CreditCardTestSupport {

    private static final Long TRANSPORT_CATEGORY_ID = 4L;

    /** Compra de 400,00 em 4 parcelas de 100,00: 20/03, 20/04, 20/05 e 20/06. */
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

    private ResultActions edit(String token, String id, Long account, Long category, String amount, String type, String description, String date, String scope) throws Exception {
        String body = "{\"accountId\":" + account + ",\"categoryId\":" + category + ",\"amount\":" + amount + ",\"type\":\"" + type
                + "\",\"description\":\"" + description + "\",\"date\":\"" + date + "\"}";
        var request = put("/api/v1/transactions/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (scope != null) request.param("scope", scope);
        return mockMvc.perform(request);
    }

    private String read(String token, String id) throws Exception {
        return mockMvc.perform(get("/api/v1/transactions/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String firstIdOf(String token, Long account) throws Exception {
        String list = mockMvc.perform(get("/api/v1/transactions").param("accountId", account.toString()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(list, "$.content[0].id").toString();
    }

    @Test
    void ca1_editOnlyThisInstallment_changesOnlyIt() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String[] ids = installmentIds(token, card);

        edit(token, ids[1], card, FOOD_CATEGORY_ID, "100.00", "EXPENSE", "Notebook corrigido", "2026-04-20", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Notebook corrigido"))
                .andExpect(jsonPath("$.installmentNumber").value(2))
                .andExpect(jsonPath("$.installmentTotal").value(4));

        assertEquals("Notebook", JsonPath.read(read(token, ids[0]), "$.description"));
        assertEquals("Notebook", JsonPath.read(read(token, ids[2]), "$.description"));
        assertEquals(JsonPath.<String>read(read(token, ids[0]), "$.installmentGroupId"),
                JsonPath.read(read(token, ids[1]), "$.installmentGroupId"));
    }

    @Test
    void ca2_editThisAndFollowing_changesDescriptionCategoryAndAmountButDateOnlyOfTheEdited() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String[] ids = installmentIds(token, card);

        edit(token, ids[1], card, TRANSPORT_CATEGORY_ID, "80.00", "EXPENSE", "Notebook B", "2026-04-25", "THIS_AND_FOLLOWING")
                .andExpect(status().isOk());

        String first = read(token, ids[0]);
        assertEquals("Notebook", JsonPath.read(first, "$.description"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(first, "$.amount"))));
        assertEquals(FOOD_CATEGORY_ID.intValue(), (int) JsonPath.<Integer>read(first, "$.categoryId"));

        String[] expectedDates = {null, "2026-04-25", "2026-05-20", "2026-06-20"};
        for (int i = 1; i < 4; i++) {
            String json = read(token, ids[i]);
            assertEquals("Notebook B", JsonPath.read(json, "$.description"));
            assertEquals(0, new BigDecimal("80.00").compareTo(money(JsonPath.read(json, "$.amount"))));
            assertEquals(TRANSPORT_CATEGORY_ID.intValue(), (int) JsonPath.<Integer>read(json, "$.categoryId"));
            assertEquals(expectedDates[i], JsonPath.read(json, "$.date"));
        }
    }

    @Test
    void ca3_editingTheDateMovesTheInstallmentToAnotherInvoice() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17); // invoicesFrom = 2026-03-10
        String[] ids = installmentIds(token, card);
        String before = invoicesJson(token, card);
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(before, "$[0].total"))));

        // 20/03 -> 15/04: sai da fatura de março e entra na de abril, junto com a parcela 2 (20/04).
        edit(token, ids[0], card, FOOD_CATEGORY_ID, "100.00", "EXPENSE", "Notebook", "2026-04-15", null).andExpect(status().isOk());

        String after = invoicesJson(token, card);
        assertEquals(0, BigDecimal.ZERO.compareTo(money(JsonPath.read(after, "$[0].total"))));
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(after, "$[1].total"))));
    }

    @Test
    void ca13_editAmountWithMoreThanTwoDecimalsOrComma_is400OnTheField() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String[] ids = installmentIds(token, card);

        edit(token, ids[0], card, FOOD_CATEGORY_ID, "10.555", "EXPENSE", "x", "2026-03-20", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.amount").isNotEmpty());
        edit(token, ids[0], card, FOOD_CATEGORY_ID, "\"10,50\"", "EXPENSE", "x", "2026-03-20", "THIS_AND_FOLLOWING")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.amount").isNotEmpty());
    }

    @Test
    void ca9_transferStill409() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long checking = createAccount(token, AccountType.CHECKING, "1000.00");
        transfer(token, checking, card, "50.00", LocalDate.of(2026, 3, 15));
        String id = firstIdOf(token, card);

        edit(token, id, card, FOOD_CATEGORY_ID, "50.00", "INCOME", "x", "2026-03-15", null).andExpect(status().isConflict());
    }

    @Test
    void editingAnInstallmentOfAnotherAccountOrType_is400OnTheField() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        Long other = createCard(token, 5, 12);
        String[] ids = installmentIds(token, card);

        edit(token, ids[0], other, FOOD_CATEGORY_ID, "100.00", "EXPENSE", "x", "2026-03-20", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.accountId").exists());
        edit(token, ids[0], card, SALARY_CATEGORY_ID, "100.00", "INCOME", "x", "2026-03-20", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.type").exists());
    }

    @Test
    void thisAndFollowingOutsideAnInstallment_is400() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        purchase(token, card, "10.00", LocalDate.of(2026, 3, 15));
        String id = firstIdOf(token, card);

        edit(token, id, card, FOOD_CATEGORY_ID, "12.00", "EXPENSE", "x", "2026-03-15", "THIS_AND_FOLLOWING")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.scope").exists());
        edit(token, id, card, FOOD_CATEGORY_ID, "12.00", "EXPENSE", "x", "2026-03-15", null).andExpect(status().isOk());
    }

    @Test
    void editingARefundedInstallment_orARangeWithOne_is409() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String[] ids = installmentIds(token, card);
        mockMvc.perform(post("/api/v1/transactions/{id}/refund", ids[2])
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-03-15\"}"))
                .andExpect(status().isCreated());

        edit(token, ids[2], card, FOOD_CATEGORY_ID, "100.00", "EXPENSE", "x", "2026-05-20", null).andExpect(status().isConflict());
        edit(token, ids[1], card, FOOD_CATEGORY_ID, "100.00", "EXPENSE", "x", "2026-04-20", "THIS_AND_FOLLOWING").andExpect(status().isConflict());
        assertEquals("Notebook", JsonPath.read(read(token, ids[1]), "$.description"));
        edit(token, ids[1], card, FOOD_CATEGORY_ID, "100.00", "EXPENSE", "so esta", "2026-04-20", null).andExpect(status().isOk());
    }
}
