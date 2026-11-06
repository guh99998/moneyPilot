package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.CreditCardTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 016, regra 21: o quadro "A pagar" soma o que falta das faturas aberta, fechada e vencida. "Hoje" é 15/03/2026. */
class InvoiceBillsSummaryIntegrationTest extends CreditCardTestSupport {

    private static final LocalDate CREATED_ON = LocalDate.of(2026, 1, 15);
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    /** Vencida (100), fechada (200) e aberta (50); a futura (60) não conta. */
    private Long cardWithEveryStatus(String token) throws Exception {
        fixToday(CREATED_ON);
        Long card = createCard(token, 10, 17);
        purchase(token, card, "100.00", LocalDate.of(2026, 1, 20));
        purchase(token, card, "200.00", LocalDate.of(2026, 2, 12));
        purchase(token, card, "50.00", LocalDate.of(2026, 3, 12));
        purchase(token, card, "60.00", LocalDate.of(2026, 5, 20));
        fixToday(TODAY);
        return card;
    }

    private String summary(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/reports/bills-summary").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void payableBill(String token, String amount, LocalDate dueDate) throws Exception {
        mockMvc.perform(post("/api/v1/bills").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + FOOD_CATEGORY_ID + ",\"amount\":" + amount + ",\"dueDate\":\"" + dueDate + "\",\"type\":\"PAYABLE\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void payable_sumsTheInvoicesThatAreOpenClosedAndOverdue() throws Exception {
        String token = registerAndLogin();
        cardWithEveryStatus(token);

        String json = summary(token);

        assertEquals(3, (int) JsonPath.<Integer>read(json, "$.payable.count"));
        assertEquals(0, new BigDecimal("350.00").compareTo(money(JsonPath.read(json, "$.payable.total"))));
        assertEquals(1, (int) JsonPath.<Integer>read(json, "$.payable.overdueCount"));
        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(json, "$.payable.overdueTotal"))));
    }

    @Test
    void payable_addsTheInvoicesToTheOrdinaryBills() throws Exception {
        String token = registerAndLogin();
        cardWithEveryStatus(token);
        payableBill(token, "70.00", LocalDate.of(2026, 3, 20));
        payableBill(token, "30.00", LocalDate.of(2026, 3, 1)); // vencido

        String json = summary(token);

        assertEquals(5, (int) JsonPath.<Integer>read(json, "$.payable.count"));
        assertEquals(0, new BigDecimal("450.00").compareTo(money(JsonPath.read(json, "$.payable.total"))));
        assertEquals(2, (int) JsonPath.<Integer>read(json, "$.payable.overdueCount"));
        assertEquals(0, new BigDecimal("130.00").compareTo(money(JsonPath.read(json, "$.payable.overdueTotal"))));
    }

    @Test
    void payable_countsOnlyWhatIsLeftAfterAPayment() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithEveryStatus(token);
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        transfer(token, checking, card, "150.00", LocalDate.of(2026, 3, 1)); // quita a vencida (100) e paga 50 da fechada

        String json = summary(token);

        assertEquals(2, (int) JsonPath.<Integer>read(json, "$.payable.count"));
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$.payable.total")))); // 150 + 50
        assertEquals(0, (int) JsonPath.<Integer>read(json, "$.payable.overdueCount"));
    }

    @Test
    void receivableAndSettled_doNotChange() throws Exception {
        String token = registerAndLogin();
        cardWithEveryStatus(token);

        String json = summary(token);

        assertEquals(0, (int) JsonPath.<Integer>read(json, "$.receivable.count"));
        assertEquals(0, (int) JsonPath.<Integer>read(json, "$.settledThisMonth.count"));
    }

    @Test
    void withoutCards_theSummaryIsTheOrdinaryOne() throws Exception {
        String token = registerAndLogin();
        payableBill(token, "70.00", LocalDate.of(2026, 3, 20));

        String json = summary(token);

        assertEquals(1, (int) JsonPath.<Integer>read(json, "$.payable.count"));
        assertEquals(0, new BigDecimal("70.00").compareTo(money(JsonPath.read(json, "$.payable.total"))));
    }
}
