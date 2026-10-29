package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MySupportTicketIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final Instant BASE = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM support_tickets");
    }

    // CA1

    @Test
    void list_returnsOnlyTheTicketsOfTheAccount_newestFirst() throws Exception {
        String email = newAddress();
        String token = loginAs(email);
        String other = newAddress();
        loginAs(other);

        insert("AAAAAA", email, userId(email), "OPEN", 0, null);
        insert("BBBBBB", email, userId(email), "IN_PROGRESS", 1, 2);
        insert("CCCCCC", other, userId(other), "OPEN", 2, null);

        mockMvc.perform(get("/api/v1/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].reference").value("BBBBBB"))
                .andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.content[0].statusChangedAt").exists())
                .andExpect(jsonPath("$.content[1].reference").value("AAAAAA"))
                .andExpect(jsonPath("$.content[1].statusChangedAt").doesNotExist());
    }

    @Test
    void list_isPagedByTen() throws Exception {
        String email = newAddress();
        String token = loginAs(email);
        long userId = userId(email);
        for (int i = 0; i < 12; i++) {
            insert(String.format("T%05d", i), email, userId, "OPEN", i, null);
        }

        mockMvc.perform(get("/api/v1/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.content[0].reference").value("T00011"));

        mockMvc.perform(get("/api/v1/support/tickets").param("page", "1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].reference").value("T00000"));
    }

    // CA2

    @Test
    void list_doesNotIncludeAnonymousTicketsWithTheEmailOfTheAccount() throws Exception {
        String email = newAddress();
        String token = loginAs(email);
        insert("ANONYM", email, null, "OPEN", 0, null);

        mockMvc.perform(get("/api/v1/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/support/tickets/ANONYM").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // CA3

    @Test
    void detail_showsMessageAndReplyEmail_butNeverEmailSentOrId() throws Exception {
        String email = newAddress();
        String token = loginAs(email);
        insert("DDDDDD", email, userId(email), "RESOLVED", 0, 3);
        jdbcTemplate.update("UPDATE support_tickets SET email_sent = FALSE WHERE reference = 'DDDDDD'");

        mockMvc.perform(get("/api/v1/support/tickets/DDDDDD").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("DDDDDD"))
                .andExpect(jsonPath("$.category").value("PROBLEMA"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.message").value("Mensagem do chamado DDDDDD"))
                .andExpect(jsonPath("$.replyTo").value(email))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.statusChangedAt").exists())
                .andExpect(jsonPath("$.emailSent").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    // CA4

    @Test
    void detail_ofAnotherAccount_isNotFound_andWithoutLoginIsUnauthorized() throws Exception {
        String owner = newAddress();
        loginAs(owner);
        String token = loginAs(newAddress());
        insert("EEEEEE", owner, userId(owner), "OPEN", 0, null);

        mockMvc.perform(get("/api/v1/support/tickets/EEEEEE").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/support/tickets/ZZZZZZ").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/support/tickets/EEEEEE")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/support/tickets")).andExpect(status().isUnauthorized());
    }

    // helpers

    private void insert(String reference, String replyTo, Long userId, String status, int createdMinutes, Integer changedMinutes) {
        Timestamp changedAt = changedMinutes == null ? null : Timestamp.from(BASE.plusSeconds(changedMinutes * 60L));
        jdbcTemplate.update("""
                        INSERT INTO support_tickets (reference, category, message, reply_to, user_id, status, email_sent, created_at, status_changed_at)
                        VALUES (?, 'PROBLEMA', ?, ?, ?, ?, TRUE, ?, ?)
                        """,
                reference, "Mensagem do chamado " + reference, replyTo, userId, status,
                Timestamp.from(BASE.plusSeconds(createdMinutes * 60L)), changedAt);
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private String loginAs(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Maria Silva"))));
        markVerified(email);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private String newAddress() {
        return "mine-" + UUID.randomUUID() + "@example.com";
    }
}
