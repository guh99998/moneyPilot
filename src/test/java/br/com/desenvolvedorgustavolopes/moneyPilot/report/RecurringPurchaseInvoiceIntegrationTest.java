package br.com.desenvolvedorgustavolopes.moneyPilot.report;

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

/**
 * Spec 016, regras 34 e 35: a cobrança recorrente ainda não gerada é projetada na fatura e nas duas
 * previsões, pelo vencimento da fatura, e nunca conta duas vezes. Cartão fecha dia 10 e vence dia 17;
 * "hoje" é 15/03/2026, então a fatura atual vai de 10/03 a 09/04 (vence 17/04) e a seguinte de 10/04 a
 * 09/05 (vence 17/05).
 */
class RecurringPurchaseInvoiceIntegrationTest extends CreditCardTestSupport {

    @Test
    void invoices_projectTheChargesUntilTheEndOfTheNextCycle() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        recurring(token, card, "50.00", 20, "2026-03-20", null); // 20/03, 20/04, 20/05...

        String json = invoicesJson(token, card);

        assertEquals(2, (int) JsonPath.<Integer>read(json, "$.length()"));
        assertAmount("50.00", JsonPath.read(json, "$[0].total"));
        assertAmount("50.00", JsonPath.read(json, "$[0].projectedTotal"));
        assertEquals("OPEN", JsonPath.read(json, "$[0].status"));
        assertAmount("50.00", JsonPath.read(json, "$[1].total"));
        assertAmount("50.00", JsonPath.read(json, "$[1].projectedTotal"));
        assertEquals("FUTURE", JsonPath.read(json, "$[1].status"));
    }

    @Test
    void invoices_afterTheChargeIsGenerated_itIsNoLongerProjectedNorCountedTwice() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        recurring(token, card, "50.00", 20, "2026-03-20", null);

        fixToday(LocalDate.of(2026, 3, 25));
        materialize(token);
        String json = invoicesJson(token, card);

        assertAmount("50.00", JsonPath.read(json, "$[0].total"));
        assertAmount("0.00", JsonPath.read(json, "$[0].projectedTotal"));
        assertAmount("50.00", JsonPath.read(json, "$[1].total"));
        assertAmount("50.00", JsonPath.read(json, "$[1].projectedTotal"));
    }

    @Test
    void invoices_sumRealPurchasesAndTheProjection() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        purchase(token, card, "100.00", LocalDate.of(2026, 3, 12));
        recurring(token, card, "50.00", 20, "2026-03-20", null);

        String json = invoicesJson(token, card);

        assertAmount("150.00", JsonPath.read(json, "$[0].total"));
        assertAmount("50.00", JsonPath.read(json, "$[0].projectedTotal"));
    }

    @Test
    void invoices_aStartInThePastProjectsOnlyWhatIsAfterInvoicesFrom() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17); // invoicesFrom = 10/03
        recurring(token, card, "50.00", 20, "2026-01-20", null);

        String json = invoicesJson(token, card);

        assertEquals(2, (int) JsonPath.<Integer>read(json, "$.length()")); // 20/03 e 20/04; 20/01 e 20/02 ficam de fora
        assertAmount("50.00", JsonPath.read(json, "$[0].projectedTotal"));
    }

    @Test
    void invoices_respectTheEndDateAndDeactivation() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        recurring(token, card, "50.00", 20, "2026-03-01", "2026-03-31");
        Long other = recurring(token, card, "70.00", 20, "2026-03-01", null);
        deactivate(token, other);

        String json = invoicesJson(token, card);

        assertAmount("50.00", JsonPath.read(json, "$[0].projectedTotal"));
        assertEquals(1, (int) JsonPath.<Integer>read(json, "$.length()")); // abril: o fim já passou e a outra está desativada
    }

    @Test
    void cashFlow_projectsTheChargeOnTheInvoiceDueDate_andDoesNotCountItTwiceAfterGenerating() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        recurring(token, card, "50.00", 14, "2026-03-14", null); // 14/03 (já vencida, ainda não gerada), 14/04, 14/05...

        String before = cashFlow(token, 90); // até 13/06: faturas de 17/04 e 17/05
        materialize(token); // 14/03 vira despesa real
        String after = cashFlow(token, 90);

        assertAmount("100.00", sumPayable(before));
        assertAmount("100.00", sumPayable(after));
    }

    @Test
    void cashFlow_doesNotProjectAChargeWhoseInvoiceDuesAfterTheHorizon() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        recurring(token, card, "50.00", 20, "2026-03-20", null);

        assertAmount("0.00", sumPayable(cashFlow(token, 30))); // até 14/04: a fatura vence em 17/04
    }

    @Test
    void monthly_projectsTheChargesInTheInvoiceDueMonths() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        recurring(token, card, "50.00", 20, "2026-03-20", null);

        String json = monthly(token, 4); // março a junho

        assertAmount("0.00", JsonPath.read(json, "$.months[0].payable"));
        assertAmount("50.00", JsonPath.read(json, "$.months[1].payable")); // 17/04
        assertAmount("50.00", JsonPath.read(json, "$.months[2].payable")); // 17/05
        assertAmount("50.00", JsonPath.read(json, "$.months[3].payable")); // 17/06
    }

    @Test
    void forecasts_doNotProjectForACardCountedInTheAvailableBalance() throws Exception {
        String token = registerAndLogin();
        Long card = createAccountWith(token, AccountType.CREDIT_CARD, "0.00", ",\"closingDay\":10,\"dueDay\":17,\"countsAsAvailable\":true");
        recurring(token, card, "50.00", 20, "2026-03-20", null);

        assertAmount("0.00", sumPayable(cashFlow(token, 90)));
    }

    @Test
    void payableInvoices_doNotIncludeTheProjectedCharges() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        purchase(token, card, "100.00", LocalDate.of(2026, 3, 12));
        recurring(token, card, "50.00", 20, "2026-03-20", null);

        String json = mockMvc.perform(get("/api/v1/invoices/payable").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(1, (int) JsonPath.<Integer>read(json, "$.length()"));
        assertAmount("100.00", JsonPath.read(json, "$[0].total"));
    }

    // ---- helpers ----

    private Long recurring(String token, Long card, String amount, int day, String start, String end) throws Exception {
        String body = "{\"accountId\":" + card + ",\"categoryId\":" + FOOD_CATEGORY_ID + ",\"description\":\"Streaming\",\"amount\":" + amount
                + ",\"dayOfMonth\":" + day + ",\"startDate\":\"" + start + "\"" + (end == null ? "" : ",\"endDate\":\"" + end + "\"") + "}";
        String response = mockMvc.perform(post("/api/v1/recurring-purchases").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void deactivate(String token, Long id) throws Exception {
        mockMvc.perform(post("/api/v1/recurring-purchases/{id}/deactivate", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void materialize(String token) throws Exception {
        mockMvc.perform(post("/api/v1/recurring-purchases/materialize").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private String cashFlow(String token, int days) throws Exception {
        return mockMvc.perform(get("/api/v1/reports/cash-flow-forecast").param("days", String.valueOf(days))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String monthly(String token, int months) throws Exception {
        return mockMvc.perform(get("/api/v1/reports/monthly-forecast").param("months", String.valueOf(months))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private BigDecimal sumPayable(String json) {
        List<Object> payables = JsonPath.read(json, "$.buckets[*].payable");
        return payables.stream().map(value -> new BigDecimal(value.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertAmount(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(money(actual)), "esperado " + expected + " mas foi " + actual);
    }
}
