package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.CreditCardTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 016, regras 39 a 42: compra parcelada pelo valor da parcela e a partir da parcela atual. */
class InstallmentAmountIntegrationTest extends CreditCardTestSupport {

    @Test
    void installmentAmount_makesEqualInstallmentsWithoutLeftoverCents() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        String json = send(token, card, "\"installmentAmount\":83.33,\"installments\":10,\"date\":\"2026-03-17\"")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertEquals(10, JsonPath.<List<Object>>read(json, "$").size());
        for (int i = 0; i < 10; i++) {
            assertEquals(0, new BigDecimal("83.33").compareTo(money(JsonPath.read(json, "$[" + i + "].amount"))));
            assertEquals(i + 1, (int) JsonPath.<Integer>read(json, "$[" + i + "].installmentNumber"));
        }
        assertEquals("2026-12-17", JsonPath.read(json, "$[9].date"));
    }

    @Test
    void currentInstallment_launchesOnlyTheRemainingOnesNumberedLikeTheOriginal() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        String json = send(token, card, "\"installmentAmount\":83.33,\"installments\":10,\"currentInstallment\":4,\"date\":\"2026-03-17\"")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertEquals(7, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals(4, (int) JsonPath.<Integer>read(json, "$[0].installmentNumber"));
        assertEquals(10, (int) JsonPath.<Integer>read(json, "$[6].installmentNumber"));
        assertEquals(10, (int) JsonPath.<Integer>read(json, "$[0].installmentTotal"));
        assertEquals("2026-03-17", JsonPath.read(json, "$[0].date"));
        assertEquals("2026-04-17", JsonPath.read(json, "$[1].date"));
        assertEquals("2026-09-17", JsonPath.read(json, "$[6].date"));
        assertEquals(JsonPath.<String>read(json, "$[0].installmentGroupId"), JsonPath.read(json, "$[6].installmentGroupId"));
    }

    @Test
    void currentInstallment_equalToTheLast_launchesASingleInstallment() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        String json = send(token, card, "\"installmentAmount\":50.00,\"installments\":6,\"currentInstallment\":6,\"date\":\"2026-03-17\"")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertEquals(1, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals(6, (int) JsonPath.<Integer>read(json, "$[0].installmentNumber"));
    }

    @Test
    void deletingOneLaunchedInstallment_removesTheLaunchedGroup() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        String json = send(token, card, "\"installmentAmount\":50.00,\"installments\":6,\"currentInstallment\":4,\"date\":\"2026-03-17\"")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String group = JsonPath.read(json, "$[0].installmentGroupId");

        mockMvc.perform(delete("/api/v1/transactions/installments/{group}", group).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        String after = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/transactions")
                        .param("accountId", card.toString()).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, (int) JsonPath.<Integer>read(after, "$.totalElements"));
    }

    @Test
    void bothAmounts_isBadRequestOnInstallmentAmount() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        send(token, card, "\"totalAmount\":100.00,\"installmentAmount\":10.00,\"installments\":10,\"date\":\"2026-03-17\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.installmentAmount").exists());
    }

    @Test
    void noAmount_isBadRequestOnTotalAmount() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        send(token, card, "\"installments\":10,\"date\":\"2026-03-17\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.totalAmount").exists());
    }

    @Test
    void currentInstallment_withTheTotalAmount_isBadRequestOnCurrentInstallment() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        send(token, card, "\"totalAmount\":100.00,\"installments\":10,\"currentInstallment\":3,\"date\":\"2026-03-17\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentInstallment").exists());
    }

    @Test
    void currentInstallment_outsideOneToTheTotal_isBadRequest() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        send(token, card, "\"installmentAmount\":10.00,\"installments\":10,\"currentInstallment\":11,\"date\":\"2026-03-17\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentInstallment").exists());
        send(token, card, "\"installmentAmount\":10.00,\"installments\":10,\"currentInstallment\":0,\"date\":\"2026-03-17\"")
                .andExpect(status().isBadRequest());
    }

    private ResultActions send(String token, Long card, String fields) throws Exception {
        return mockMvc.perform(post("/api/v1/transactions/installments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":" + card + ",\"categoryId\":" + FOOD_CATEGORY_ID + ",\"description\":\"Notebook\"," + fields + "}"));
    }
}
