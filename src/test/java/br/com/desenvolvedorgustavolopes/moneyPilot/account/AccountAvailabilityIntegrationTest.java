package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 015: opção "contar no saldo disponível" por conta. */
class AccountAvailabilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void create_withoutTheField_usesTheDefaultOfTheType() throws Exception {
        String token = registerAndLogin();

        for (AccountType type : AccountType.values()) {
            boolean expected = type != AccountType.INVESTMENT;
            mockMvc.perform(post("/api/v1/accounts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(type, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.countsAsAvailable").value(expected));
        }
    }

    @Test
    void create_withTheField_keepsTheChoice() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AccountType.INVESTMENT, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countsAsAvailable").value(true));
    }

    @Test
    void update_withoutTheFieldAndSameType_keepsTheCurrentValue() throws Exception {
        String token = registerAndLogin();
        Long id = create(token, AccountType.CHECKING, false);

        mockMvc.perform(put("/api/v1/accounts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AccountType.CHECKING, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countsAsAvailable").value(false));
    }

    @Test
    void update_withoutTheFieldAndNewType_usesTheDefaultOfTheNewType() throws Exception {
        String token = registerAndLogin();
        Long id = create(token, AccountType.CHECKING, null);

        mockMvc.perform(put("/api/v1/accounts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AccountType.INVESTMENT, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countsAsAvailable").value(false));

        mockMvc.perform(put("/api/v1/accounts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AccountType.SAVING, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countsAsAvailable").value(true));
    }

    @Test
    void update_withTheField_setsIt() throws Exception {
        String token = registerAndLogin();
        Long id = create(token, AccountType.INVESTMENT, null);

        mockMvc.perform(put("/api/v1/accounts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AccountType.INVESTMENT, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countsAsAvailable").value(true));

        mockMvc.perform(get("/api/v1/accounts/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countsAsAvailable").value(true));
    }

    @Test
    void update_ofAnotherUsersAccount_is404() throws Exception {
        String owner = registerAndLogin();
        String other = registerAndLogin();
        Long id = create(owner, AccountType.CHECKING, null);

        mockMvc.perform(put("/api/v1/accounts/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(AccountType.CHECKING, false)))
                .andExpect(status().isNotFound());
    }

    @Test
    void migration_marksExistingInvestmentAccountsAsUnchecked() throws Exception {
        registerAndLogin();
        Long userId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM users", Long.class);
        // Linhas como existiam antes da coluna: sem o campo, valem o DEFAULT da migration.
        for (String type : new String[]{"CHECKING", "SAVING", "CASH", "CREDIT_CARD", "INVESTMENT"}) {
            jdbcTemplate.update("INSERT INTO accounts (user_id, name, type, initial_balance, created_at, updated_at) "
                    + "VALUES (?, ?, ?, 0, NOW(), NOW())", userId, "legado " + type, type);
        }
        String migration = new String(new ClassPathResource("db/migration/V19__add_counts_as_available_to_accounts.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        // O UPDATE da migration reaplicado sobre linhas que ainda valem o DEFAULT.
        String update = migration.lines().filter(l -> l.startsWith("UPDATE")).findFirst().orElseThrow();
        jdbcTemplate.update("UPDATE accounts SET counts_as_available = TRUE WHERE user_id = ?", userId);
        jdbcTemplate.update(update);

        assertEquals(false, jdbcTemplate.queryForObject(
                "SELECT counts_as_available FROM accounts WHERE user_id = ? AND type = 'INVESTMENT'", Boolean.class, userId));
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE user_id = ? AND type <> 'INVESTMENT' AND counts_as_available", Integer.class, userId));
    }

    // ---- helpers ----

    private String body(AccountType type, Boolean counts) {
        return "{\"name\":\"Conta\",\"accountType\":\"" + type + "\",\"initialBalance\":10.00"
                + (counts == null ? "" : ",\"countsAsAvailable\":" + counts) + "}";
    }

    private Long create(String token, AccountType type, Boolean counts) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(type, counts)))
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
