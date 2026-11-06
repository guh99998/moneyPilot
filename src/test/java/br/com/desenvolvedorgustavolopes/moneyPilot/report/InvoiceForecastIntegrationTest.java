package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.CreditCardTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 016, regra 10: a fatura não quitada de um cartão fora do disponível entra como saída no
 * vencimento nas duas previsões. Cartão fecha dia 10 e vence dia 17; "hoje" é 15/03/2026.
 */
class InvoiceForecastIntegrationTest extends CreditCardTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    @Test
    void cashFlow_projectsTheOpenInvoiceOnItsDueDate() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        purchase(token, card, "200.00", LocalDate.of(2026, 3, 12)); // fatura atual: vence 17/04

        String json = cashFlow(token, 60);

        assertEquals(0, new BigDecimal("200.00").compareTo(sumPayable(json)));
        // 17/04 está a 33 dias de hoje: bucket 4 (dias 28 a 34).
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$.buckets[4].payable"))));
    }

    @Test
    void monthly_projectsTheOpenInvoiceInTheDueMonth() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        purchase(token, card, "200.00", LocalDate.of(2026, 3, 12));

        String json = monthly(token, 3);

        assertEquals(0, BigDecimal.ZERO.compareTo(money(JsonPath.read(json, "$.months[0].payable"))));
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$.months[1].payable"))));
    }

    @Test
    void invoiceBeyondTheHorizon_isNotProjected() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        purchase(token, card, "200.00", LocalDate.of(2026, 3, 12));

        assertEquals(0, BigDecimal.ZERO.compareTo(sumPayable(cashFlow(token, 30)))); // até 13/04
    }

    @Test
    void cardCountedInTheAvailableBalance_doesNotProject() throws Exception {
        String token = registerAndLogin();
        Long card = createAccountWith(token, AccountType.CREDIT_CARD, "0.00", ",\"closingDay\":10,\"dueDay\":17,\"countsAsAvailable\":true");
        purchase(token, card, "200.00", LocalDate.of(2026, 3, 12));

        String json = cashFlow(token, 60);

        assertEquals(0, BigDecimal.ZERO.compareTo(sumPayable(json)));
        assertEquals(0, new BigDecimal("-200.00").compareTo(money(JsonPath.read(json, "$.currentBalance"))));
    }

    @Test
    void cardWithoutDays_doesNotProject() throws Exception {
        String token = registerAndLogin();
        Long card = createAccount(token, AccountType.CREDIT_CARD, "0.00");
        purchase(token, card, "200.00", LocalDate.of(2026, 3, 12));

        assertEquals(0, BigDecimal.ZERO.compareTo(sumPayable(cashFlow(token, 60))));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(JsonPath.read(monthly(token, 3), "$.months[1].payable"))));
    }

    @Test
    void overdueInvoice_landsInTheFirstPeriodAndCountsAsOverdue() throws Exception {
        String token = registerAndLogin();
        fixToday(LocalDate.of(2026, 1, 15));
        Long card = createCard(token, 10, 17);
        purchase(token, card, "100.00", LocalDate.of(2026, 1, 20)); // fatura A: vence 17/02
        fixToday(TODAY);

        String cashFlow = cashFlow(token, 30);
        String monthly = monthly(token, 3);

        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(cashFlow, "$.buckets[0].payable"))));
        assertEquals(1, (int) JsonPath.<Integer>read(cashFlow, "$.overdue.count"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(cashFlow, "$.overdue.payable"))));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(monthly, "$.months[0].payable"))));
        assertEquals(1, (int) JsonPath.<Integer>read(monthly, "$.overdue.count"));
    }

    @Test
    void paying_reducesTheAvailableBalanceAndTheInvoiceWithoutCountingTwice() throws Exception {
        String token = registerAndLogin();
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        Long card = createCard(token, 10, 17);
        purchase(token, card, "200.00", LocalDate.of(2026, 3, 12));

        String before = cashFlow(token, 60);
        transfer(token, checking, card, "80.00", TODAY);
        String after = cashFlow(token, 60);

        // Antes: 5000 - 200 da fatura = 4800. Depois: 4920 disponíveis - 120 que faltam = 4800.
        assertEquals(0, new BigDecimal("4800.00").compareTo(lastBalance(before)));
        assertEquals(0, new BigDecimal("4920.00").compareTo(money(JsonPath.read(after, "$.currentBalance"))));
        assertEquals(0, new BigDecimal("120.00").compareTo(sumPayable(after)));
        assertEquals(0, new BigDecimal("4800.00").compareTo(lastBalance(after)));
    }

    @Test
    void paidInvoice_isNotProjected() throws Exception {
        String token = registerAndLogin();
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        Long card = createCard(token, 10, 17);
        purchase(token, card, "200.00", LocalDate.of(2026, 3, 12));
        transfer(token, checking, card, "200.00", TODAY);

        assertEquals(0, BigDecimal.ZERO.compareTo(sumPayable(cashFlow(token, 60))));
    }

    @Test
    void futureInstallments_appearInTheirOwnInvoices() throws Exception {
        String token = registerAndLogin();
        Long card = createCard(token, 10, 17);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/transactions/installments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + card + ",\"categoryId\":3,\"description\":\"TV\",\"totalAmount\":300.00,\"installments\":3,\"date\":\"2026-03-12\"}"))
                .andExpect(status().isCreated());

        String json = monthly(token, 4); // março a junho

        // Faturas: março (parcela 1) vence em 17/04; abril vence em 17/05; maio vence em 17/06.
        assertEquals(0, BigDecimal.ZERO.compareTo(money(JsonPath.read(json, "$.months[0].payable"))));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(json, "$.months[1].payable"))));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(json, "$.months[2].payable"))));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(json, "$.months[3].payable"))));
    }

    // ---- helpers ----

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
        return payables.stream().map(money -> new BigDecimal(money.toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal lastBalance(String json) {
        List<Object> balances = JsonPath.read(json, "$.buckets[*].projectedBalance");
        return new BigDecimal(balances.get(balances.size() - 1).toString());
    }
}
