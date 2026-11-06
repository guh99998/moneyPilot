package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 016: fatura calculada em leitura (regras 3 a 7). Cartão fecha dia 10 e vence dia 17; "hoje" é 15/03/2026. */
class InvoiceIntegrationTest extends CreditCardTestSupport {

    private static final LocalDate CREATED_ON = LocalDate.of(2026, 1, 15); // invoicesFrom = 2026-01-10
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    /** Ciclo A (10/01 a 09/02): 100 + 50 - 30 = 120. Ciclo B (10/02 a 09/03): 200. Ciclo C (atual, 10/03 a 09/04): vazio. */
    private Long cardWithHistory(String token) throws Exception {
        fixToday(CREATED_ON);
        Long card = createCard(token, 10, 17);
        purchase(token, card, "100.00", LocalDate.of(2026, 1, 12));
        purchase(token, card, "50.00", LocalDate.of(2026, 1, 20));
        refund(token, card, "30.00", LocalDate.of(2026, 2, 5));
        purchase(token, card, "200.00", LocalDate.of(2026, 2, 10)); // dia do fechamento: cai no ciclo B
        fixToday(TODAY);
        return card;
    }

    @Test
    void invoices_groupPurchasesByCycleAndSubtractRefunds() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithHistory(token);

        String json = invoicesJson(token, card);

        assertEquals(3, JsonPath.<List<Object>>read(json, "$").size());
        assertEquals("2026-01-10", JsonPath.read(json, "$[0].periodStart"));
        assertEquals("2026-02-09", JsonPath.read(json, "$[0].periodEnd"));
        assertEquals("2026-02-10", JsonPath.read(json, "$[0].closingDate"));
        assertEquals("2026-02-17", JsonPath.read(json, "$[0].dueDate"));
        assertEquals(0, new BigDecimal("120.00").compareTo(money(JsonPath.read(json, "$[0].total"))));
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$[1].total"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(JsonPath.read(json, "$[2].total"))));
    }

    @Test
    void invoices_statusFollowsTodayAndDueDate() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithHistory(token);

        String json = invoicesJson(token, card);

        assertEquals("OVERDUE", JsonPath.read(json, "$[0].status")); // venceu em 17/02
        assertEquals("CLOSED", JsonPath.read(json, "$[1].status"));  // vence em 17/03
        assertEquals("OPEN", JsonPath.read(json, "$[2].status"));
    }

    @Test
    void invoices_ignorePurchasesBeforeInvoicesFromAndTheInitialBalance() throws Exception {
        String token = registerAndLogin();
        fixToday(CREATED_ON);
        Long card = createAccountWith(token, AccountType.CREDIT_CARD, "-500.00", ",\"closingDay\":10,\"dueDay\":17");
        purchase(token, card, "999.00", LocalDate.of(2026, 1, 5)); // antes de invoicesFrom (10/01)
        purchase(token, card, "40.00", LocalDate.of(2026, 1, 11));
        fixToday(TODAY);

        String json = invoicesJson(token, card);

        assertEquals(0, new BigDecimal("40.00").compareTo(money(JsonPath.read(json, "$[0].total"))));
        assertEquals("2026-01-10", JsonPath.read(json, "$[0].periodStart"));
    }

    @Test
    void invoices_partialPaymentLeavesTheRestOnTheOldestInvoice() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithHistory(token);
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        transfer(token, checking, card, "100.00", LocalDate.of(2026, 3, 1));

        String json = invoicesJson(token, card);

        assertEquals(0, new BigDecimal("100.00").compareTo(money(JsonPath.read(json, "$[0].paid"))));
        assertEquals(0, new BigDecimal("20.00").compareTo(money(JsonPath.read(json, "$[0].remaining"))));
        assertEquals("OVERDUE", JsonPath.read(json, "$[0].status"));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(JsonPath.read(json, "$[1].paid"))));
        assertEquals(0, new BigDecimal("200.00").compareTo(money(JsonPath.read(json, "$[1].remaining"))));
    }

    @Test
    void invoices_paymentIsNotAnExpenseOnTheCard() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithHistory(token);
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        transfer(token, checking, card, "120.00", LocalDate.of(2026, 3, 1));

        String json = invoicesJson(token, card);

        assertEquals(0, new BigDecimal("120.00").compareTo(money(JsonPath.read(json, "$[0].total"))));
        assertEquals("PAID", JsonPath.read(json, "$[0].status"));
    }

    @Test
    void invoices_overpaymentBecomesCreditForTheFollowingInvoices() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithHistory(token);
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        transfer(token, checking, card, "400.00", LocalDate.of(2026, 3, 1));
        purchase(token, card, "100.00", LocalDate.of(2026, 3, 12)); // ciclo atual

        String json = invoicesJson(token, card);

        assertEquals("PAID", JsonPath.read(json, "$[0].status"));
        assertEquals("PAID", JsonPath.read(json, "$[1].status"));
        // 400 pagos - 320 das duas faturas = 80 de crédito para a fatura atual.
        assertEquals(0, new BigDecimal("80.00").compareTo(money(JsonPath.read(json, "$[2].paid"))));
        assertEquals(0, new BigDecimal("20.00").compareTo(money(JsonPath.read(json, "$[2].remaining"))));
    }

    @Test
    void invoices_currentInvoiceStaysOpenEvenWhenPaid() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithHistory(token);
        Long checking = createAccount(token, AccountType.CHECKING, "5000.00");
        transfer(token, checking, card, "370.00", LocalDate.of(2026, 3, 1));
        purchase(token, card, "50.00", LocalDate.of(2026, 3, 12));

        String json = invoicesJson(token, card);

        assertEquals(0, new BigDecimal("50.00").compareTo(money(JsonPath.read(json, "$[2].paid"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(money(JsonPath.read(json, "$[2].remaining"))));
        assertEquals("OPEN", JsonPath.read(json, "$[2].status"));
    }

    @Test
    void invoices_refundBiggerThanPurchasesBecomesCreditForTheNextInvoice() throws Exception {
        String token = registerAndLogin();
        fixToday(CREATED_ON);
        Long card = createCard(token, 10, 17);
        purchase(token, card, "20.00", LocalDate.of(2026, 1, 12));
        refund(token, card, "50.00", LocalDate.of(2026, 1, 13)); // fatura A: -30
        purchase(token, card, "100.00", LocalDate.of(2026, 2, 12)); // fatura B: 100
        fixToday(TODAY);

        String json = invoicesJson(token, card);

        assertEquals(0, new BigDecimal("-30.00").compareTo(money(JsonPath.read(json, "$[0].total"))));
        assertEquals("PAID", JsonPath.read(json, "$[0].status"));
        assertEquals(0, new BigDecimal("30.00").compareTo(money(JsonPath.read(json, "$[1].paid"))));
        assertEquals(0, new BigDecimal("70.00").compareTo(money(JsonPath.read(json, "$[1].remaining"))));
    }

    @Test
    void invoices_futureInvoiceHasFutureStatus() throws Exception {
        String token = registerAndLogin();
        fixToday(TODAY);
        Long card = createCard(token, 10, 17);
        purchase(token, card, "60.00", LocalDate.of(2026, 5, 20));

        String json = invoicesJson(token, card);

        assertEquals("OPEN", JsonPath.read(json, "$[0].status"));
        assertEquals("FUTURE", JsonPath.read(json, "$[1].status"));
        assertEquals("2026-05-10", JsonPath.read(json, "$[1].periodStart"));
    }

    @Test
    void invoices_ofAnAccountWithoutDays_is409() throws Exception {
        String token = registerAndLogin();
        fixToday(TODAY);
        Long checking = createAccount(token, AccountType.CHECKING, "0.00");
        Long cardWithoutDays = createAccount(token, AccountType.CREDIT_CARD, "0.00");

        for (Long id : new Long[]{checking, cardWithoutDays}) {
            mockMvc.perform(get("/api/v1/accounts/{id}/invoices", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void invoices_ofAnotherUsersAccount_is404() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        fixToday(TODAY);
        Long card = createCard(owner, 10, 17);

        mockMvc.perform(get("/api/v1/accounts/{id}/invoices", card).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void accountBalance_keepsCountingEverything() throws Exception {
        String token = registerAndLogin();
        Long card = cardWithHistory(token);

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", card).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(-320.00));
    }
}
