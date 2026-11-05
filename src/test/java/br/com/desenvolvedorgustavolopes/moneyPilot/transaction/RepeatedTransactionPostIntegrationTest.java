package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documenta o comportamento atual (sem chave de idempotência): repetir o mesmo {@code POST /transactions}
 * cria dois lançamentos, e o saldo conta os dois. Se um dia a API passar a deduplicar, este teste muda junto
 * com a spec de idempotência.
 */
class RepeatedTransactionPostIntegrationTest extends AbstractIntegrationTest {

    private static final Long FOOD_CATEGORY_ID = 3L; // "Alimentação", global, EXPENSE

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void postingTheSameTransactionTwice_createsTwoTransactionsAndCountsBoth() throws Exception {
        String token = registerAndLogin();
        Long accountId = createAccount(token);
        TransactionRequest request = new TransactionRequest(accountId, FOOD_CATEGORY_ID, new BigDecimal("40.00"),
                TransactionType.EXPENSE, "Mercado", LocalDate.of(2026, 1, 1));

        long first = postTransaction(token, request);
        long second = postTransaction(token, request);

        assertNotEquals(first, second);
        MvcResult balance = mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        BigDecimal current = new BigDecimal(JsonPath.read(balance.getResponse().getContentAsString(), "$.currentBalance").toString());
        assertEquals(0, new BigDecimal("20.00").compareTo(current));
    }

    private long postTransaction(String token, TransactionRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String registerAndLogin() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String password = "minha frase secreta";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Test User"))))
                .andExpect(status().isAccepted());
        markVerified(email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private Long createAccount(String token) throws Exception {
        AccountRequest request = new AccountRequest("Conta " + UUID.randomUUID(), AccountType.CHECKING, new BigDecimal("100.00"));
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
