package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.CreditCardTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 016, regra 22: só fatura fechada ou vencida gera alerta; a aberta não. Cartão fecha dia 10 e vence dia 17; "hoje" é 15/03/2026. */
class InvoiceAlertIntegrationTest extends CreditCardTestSupport {

    private static final LocalDate CREATED_ON = LocalDate.of(2026, 1, 15);
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    /** Vencida (100, vence 17/02), fechada (200, vence 17/03, em 2 dias) e aberta (50). */
    private Long cardWithEveryStatus(String token) throws Exception {
        fixToday(CREATED_ON);
        Long card = createCard(token, 10, 17);
        purchase(token, card, "100.00", LocalDate.of(2026, 1, 20));
        purchase(token, card, "200.00", LocalDate.of(2026, 2, 12));
        purchase(token, card, "50.00", LocalDate.of(2026, 3, 12));
        fixToday(TODAY);
        return card;
    }

    private String alerts(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/alerts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> keys(String json) {
        return JsonPath.read(json, "$.alerts[*].key");
    }

    @Test
    void overdueInvoice_entersThePayableOverdueAlert() throws Exception {
        String token = registerAndLogin();
        cardWithEveryStatus(token);

        String json = alerts(token);

        int index = keys(json).indexOf("PAYABLE_OVERDUE");
        assertEquals(true, index >= 0, "alert present");
        assertEquals(1, (int) JsonPath.<Integer>read(json, "$.alerts[" + index + "].count"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(json, "$.alerts[" + index + "].total"))));
    }

    @Test
    void closedInvoiceDueWithinSevenDays_entersTheDueSoonAlert() throws Exception {
        String token = registerAndLogin();
        cardWithEveryStatus(token);

        String json = alerts(token);

        int index = keys(json).indexOf("PAYABLE_DUE_SOON");
        assertEquals(true, index >= 0, "alert present");
        assertEquals(1, (int) JsonPath.<Integer>read(json, "$.alerts[" + index + "].count"));
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$.alerts[" + index + "].total"))));
        assertEquals("2026-03-17", JsonPath.read(json, "$.alerts[" + index + "].dueUntil"));
    }

    @Test
    void openInvoice_neverAlerts() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        purchase(token, card, "80.00", LocalDate.of(2026, 3, 12)); // aberta, vence 17/04

        assertEquals(0, keys(alerts(token)).size());
    }

    @Test
    void closedInvoiceDueAfterSevenDays_doesNotAlertYet() throws Exception {
        String token = registerAndLogin();
        fixToday(LocalDate.of(2026, 2, 10));
        Long card = createCard(token, 1, 25); // invoicesFrom = 01/02
        purchase(token, card, "80.00", LocalDate.of(2026, 2, 12));
        fixToday(TODAY); // fechou em 01/03 e vence em 25/03: daqui a 10 dias

        assertEquals(0, keys(alerts(token)).size());
    }

    @Test
    void invoices_addToTheOrdinaryBillsInTheSameAlert() throws Exception {
        String token = registerAndLogin();
        cardWithEveryStatus(token);
        mockMvc.perform(post("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + FOOD_CATEGORY_ID + ",\"amount\":30.00,\"dueDate\":\"2026-03-01\",\"type\":\"PAYABLE\"}"))
                .andExpect(status().isCreated());

        String json = alerts(token);

        int index = keys(json).indexOf("PAYABLE_OVERDUE");
        assertEquals(2, (int) JsonPath.<Integer>read(json, "$.alerts[" + index + "].count"));
        assertEquals(0, new BigDecimal("130.00").compareTo(money(JsonPath.read(json, "$.alerts[" + index + "].total"))));
    }

    @Test
    void payingTheInvoices_clearsTheAlerts() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithEveryStatus(token);
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");

        transfer(token, checking, card, "300.00", TODAY); // quita a vencida e a fechada

        assertEquals(0, keys(alerts(token)).size());
    }
}
