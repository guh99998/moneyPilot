package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.BalanceResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void createTransfer_createsOppositeLegsAndUpdatesBothBalances() throws Exception {
        String token = registerAndLogin();
        Long accountA = createAccount(token, new BigDecimal("100.00"));
        Long accountB = createAccount(token, new BigDecimal("50.00"));

        MvcResult transferResult = mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(accountA, accountB, new BigDecimal("30.00"), LocalDate.of(2026, 1, 1), "Transferência"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accountId").value(accountA))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[1].accountId").value(accountB))
                .andExpect(jsonPath("$[1].type").value("INCOME"))
                .andReturn();

        String body = transferResult.getResponse().getContentAsString();
        String expenseLegGroupId = JsonPath.read(body, "$[0].transferGroupId");
        String incomeLegGroupId = JsonPath.read(body, "$[1].transferGroupId");

        assertEquals(expenseLegGroupId, incomeLegGroupId);

        assertEquals(0, new BigDecimal("70.00").compareTo(getBalance(token, accountA).currentBalance()));
        assertEquals(0, new BigDecimal("80.00").compareTo(getBalance(token, accountB).currentBalance()));
    }

    @Test
    void deletingOneLegOfTransfer_deletesBothLegsAndRestoresBalances() throws Exception {
        String token = registerAndLogin();
        Long accountA = createAccount(token, new BigDecimal("100.00"));
        Long accountB = createAccount(token, new BigDecimal("50.00"));

        MvcResult transferResult = mockMvc.perform(post("/api/v1/transactions/transfers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransferRequest(accountA, accountB, new BigDecimal("30.00"), LocalDate.of(2026, 1, 1), "Transferência"))))
                .andExpect(status().isCreated())
                .andReturn();

        String body = transferResult.getResponse().getContentAsString();
        Long expenseLegId = ((Number) JsonPath.read(body, "$[0].id")).longValue();
        Long incomeLegId = ((Number) JsonPath.read(body, "$[1].id")).longValue();

        mockMvc.perform(delete("/api/v1/transactions/{id}", expenseLegId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions/{id}", incomeLegId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        assertEquals(0, new BigDecimal("100.00").compareTo(getBalance(token, accountA).currentBalance()));
        assertEquals(0, new BigDecimal("50.00").compareTo(getBalance(token, accountB).currentBalance()));
    }

    private BalanceResponse getBalance(String token, Long accountId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), BalanceResponse.class);
    }

    private String registerAndLogin() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        String password = "senha12345";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Test User"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private Long createAccount(String token, BigDecimal initialBalance) throws Exception {
        AccountRequest request = new AccountRequest("Conta " + UUID.randomUUID(), AccountType.CHECKING, initialBalance);

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
