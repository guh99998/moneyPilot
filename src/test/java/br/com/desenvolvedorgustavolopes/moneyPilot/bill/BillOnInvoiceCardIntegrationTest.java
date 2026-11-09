package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.CreditCardTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 016, regra 38: título e recorrência de título em cartão com fechamento e vencimento são
 * recusados (a assinatura no cartão é a compra recorrente), mas baixar um título no cartão continua
 * permitido, e o que já existe não muda.
 */
class BillOnInvoiceCardIntegrationTest extends CreditCardTestSupport {

    @Test
    void createBill_onACardWithDays_isBadRequestOnAccountId() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        postJson(token, "/api/v1/bills", billBody(card))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists());
    }

    @Test
    void createInstallmentPlan_onACardWithDays_isBadRequestOnAccountId() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);

        postJson(token, "/api/v1/bills/installments", "{\"accountId\":" + card + ",\"categoryId\":" + FOOD_CATEGORY_ID
                + ",\"description\":\"TV\",\"totalAmount\":300.00,\"installments\":3,\"firstDueDate\":\"2026-04-10\",\"type\":\"PAYABLE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists());
    }

    @Test
    void updateBill_toACardWithDays_isBadRequestOnAccountId() throws Exception {
        String token = registerAndLogin();
        Long checking = createAccount(token, AccountType.CHECKING, "100.00");
        Long card = createCard(token, 10, 17);
        Long billId = createBill(token, checking);

        putJson(token, "/api/v1/bills/" + billId, billBody(card))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists());
    }

    @Test
    void billRecurrence_onACardWithDays_isBadRequestOnCreateAndUpdate() throws Exception {
        String token = registerAndLogin();
        Long checking = createAccount(token, AccountType.CHECKING, "100.00");
        Long card = createCard(token, 10, 17);

        postJson(token, "/api/v1/bill-recurrences", recurrenceBody(card))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists());

        String created = postJson(token, "/api/v1/bill-recurrences", recurrenceBody(checking))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.id");
        putJson(token, "/api/v1/bill-recurrences/" + id.longValue(), recurrenceBody(card))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.accountId").exists());
    }

    @Test
    void aCardWithoutDays_stillAcceptsBillsAndRecurrences() throws Exception {
        String token = registerAndLogin();
        Long plainCard = createAccount(token, AccountType.CREDIT_CARD, "0.00");

        postJson(token, "/api/v1/bills", billBody(plainCard)).andExpect(status().isCreated());
        postJson(token, "/api/v1/bill-recurrences", recurrenceBody(plainCard)).andExpect(status().isCreated());
    }

    @Test
    void settlingABill_intoACardWithDays_isStillAllowed() throws Exception {
        String token = registerAndLogin();
        Long checking = createAccount(token, AccountType.CHECKING, "500.00");
        Long card = createCard(token, 10, 17);
        Long billId = createBill(token, checking);

        postJson(token, "/api/v1/bills/" + billId + "/settle", "{\"accountId\":" + card + ",\"amount\":50.00}")
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    private String billBody(Long accountId) {
        return "{\"accountId\":" + accountId + ",\"categoryId\":" + FOOD_CATEGORY_ID
                + ",\"description\":\"Boleto\",\"amount\":50.00,\"dueDate\":\"2026-04-10\",\"type\":\"PAYABLE\"}";
    }

    private String recurrenceBody(Long accountId) {
        return "{\"accountId\":" + accountId + ",\"categoryId\":" + FOOD_CATEGORY_ID
                + ",\"description\":\"Assinatura\",\"amount\":50.00,\"type\":\"PAYABLE\",\"dayOfMonth\":10,\"startDate\":\"2026-04-01\"}";
    }

    private Long createBill(String token, Long accountId) throws Exception {
        String created = postJson(token, "/api/v1/bills", billBody(accountId)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }

    private ResultActions postJson(String token, String url, String json) throws Exception {
        return mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(String token, String url, String json) throws Exception {
        return mockMvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
