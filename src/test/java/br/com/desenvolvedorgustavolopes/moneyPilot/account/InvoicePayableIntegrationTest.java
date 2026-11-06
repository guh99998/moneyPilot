package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 016, regra 17: faturas a pagar (aberta, fechada e vencida) para "A pagar e receber". Cartão fecha dia 10 e vence dia 17; "hoje" é 15/03/2026. */
class InvoicePayableIntegrationTest extends CreditCardTestSupport {

    private static final LocalDate CREATED_ON = LocalDate.of(2026, 1, 15);
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    /** Vencida (100, vence 17/02), fechada (200, vence 17/03), aberta (50, vence 17/04) e futura (60, vence 17/06). */
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

    private String payableJson(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/invoices/payable").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void payable_listsOverdueClosedAndOpenInvoicesByDueDate_andLeavesTheFutureOut() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithEveryStatus(token);

        String json = payableJson(token);

        assertEquals(3, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals("OVERDUE", JsonPath.read(json, "$[0].status"));
        assertEquals("2026-02-17", JsonPath.read(json, "$[0].dueDate"));
        assertEquals("CLOSED", JsonPath.read(json, "$[1].status"));
        assertEquals("2026-03-17", JsonPath.read(json, "$[1].dueDate"));
        assertEquals("OPEN", JsonPath.read(json, "$[2].status"));
        assertEquals("2026-04-17", JsonPath.read(json, "$[2].dueDate"));
        assertEquals(card.longValue(), ((Number) JsonPath.read(json, "$[0].accountId")).longValue());
    }

    @Test
    void payable_carriesTheCardNameAndTheInvoiceAmounts() throws Exception {
        String token = registerAndLogin();
        cardWithEveryStatus(token);

        String json = payableJson(token);

        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$[1].total"))));
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$[1].remaining"))));
        assertEquals("2026-02-10", JsonPath.read(json, "$[1].periodStart"));
        assertEquals("2026-03-10", JsonPath.read(json, "$[1].closingDate"));
        assertEquals(true, JsonPath.<String>read(json, "$[0].accountName").startsWith("Conta "));
    }

    @Test
    void payable_showsWhatIsLeftAfterAPartialPayment_andDropsAPaidInvoice() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithEveryStatus(token);
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        transfer(token, checking, card, "130.00", LocalDate.of(2026, 3, 1)); // quita a vencida (100) e paga 30 da fechada

        String json = payableJson(token);

        assertEquals(2, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals("CLOSED", JsonPath.read(json, "$[0].status"));
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$[0].total"))));
        assertEquals(0, new BigDecimal("30.00").compareTo(money(JsonPath.read(json, "$[0].paid"))));
        assertEquals(0, new BigDecimal("170.00").compareTo(money(JsonPath.read(json, "$[0].remaining"))));
        assertEquals("OPEN", JsonPath.read(json, "$[1].status"));
    }

    @Test
    void payable_includesCardsThatStillCountInTheAvailableBalance() throws Exception {
        String token = registerAndLogin();
        createAccountWith(token, AccountType.CREDIT_CARD, "0.00", ",\"closingDay\":10,\"dueDay\":17,\"countsAsAvailable\":true");
        Long card = createAccountWith(token, AccountType.CREDIT_CARD, "0.00", ",\"closingDay\":10,\"dueDay\":17,\"countsAsAvailable\":true");
        purchase(token, card, "80.00", LocalDate.of(2026, 3, 12));

        String json = payableJson(token);

        assertEquals(1, JsonPath.<List<Object>>read(json, "$").size()); // o outro cartão não tem nada a pagar
        assertEquals(0, new BigDecimal("80.00").compareTo(money(JsonPath.read(json, "$[0].total"))));
    }

    @Test
    void payable_ignoresCardsWithoutDaysAndOtherAccounts() throws Exception {
        String token = registerAndLogin();
        Long cardWithoutDays = createAccount(token, AccountType.CREDIT_CARD, "0.00");
        purchase(token, cardWithoutDays, "300.00", LocalDate.of(2026, 3, 12));
        Long checking = createAccount(token, AccountType.CHECKING, "0.00");
        purchase(token, checking, "40.00", LocalDate.of(2026, 3, 12));

        assertEquals(0, JsonPath.<List<Object>>read(payableJson(token), "$").size());
    }

    @Test
    void payable_neverShowsAnotherUsersInvoices() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        cardWithEveryStatus(owner);

        assertEquals(0, JsonPath.<List<Object>>read(payableJson(other), "$").size());
    }

    @Test
    void payable_withoutLogin_is401() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/payable")).andExpect(status().isUnauthorized());
    }
}
