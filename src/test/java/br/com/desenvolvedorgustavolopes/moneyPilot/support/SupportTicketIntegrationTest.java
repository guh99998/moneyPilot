package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.RecordingEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.admin.emails= Admin@Example.test ,second.admin@example.test")
class SupportTicketIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final String MESSAGE = "O saldo da minha conta não bate com o extrato.";
    private static final String ADMIN_EMAIL = "admin@example.test";
    private static final String REFERENCE = "[2-9A-HJKMNP-Z]{6}";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Instant now;
    private String sender;

    @BeforeEach
    void setUp() {
        sender = newAddress();
        now = Instant.parse("2026-10-27T15:00:00Z");
        when(clock.getZone()).thenReturn(SAO_PAULO);
        when(clock.instant()).thenAnswer(invocation -> now);
        emailSender.clear();
        jdbcTemplate.update("DELETE FROM support_tickets");
    }

    @AfterEach
    void stopFailing() {
        emailSender.failFromNowOn(false);
    }

    // CA1

    @Test
    void anonymousTicket_isSavedOpen_withTheReferenceOfTheEmail() throws Exception {
        String response = send(null, "PROBLEMA", sender)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(response, "$.reference");

        Map<String, Object> row = onlyTicket();
        assertEquals(reference, row.get("reference"));
        assertEquals("PROBLEMA", row.get("category"));
        assertEquals(MESSAGE, row.get("message"));
        assertEquals(sender, row.get("reply_to"));
        assertNull(row.get("user_id"));
        assertEquals("OPEN", row.get("status"));
        assertEquals(true, row.get("email_sent"));
        assertNull(row.get("status_changed_at"));
        assertTrue(onlyEmail().subject().contains(reference));
    }

    @Test
    void loggedTicket_isSavedOpen_linkedToTheAccount_withTheAccountEmail() throws Exception {
        String email = newAddress();
        String token = loginAs(email, "Maria Silva");

        String response = send(token, "DUVIDA", null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> row = onlyTicket();
        assertEquals(JsonPath.read(response, "$.reference"), row.get("reference"));
        assertEquals(email, row.get("reply_to"));
        assertEquals("OPEN", row.get("status"));
        assertEquals(jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email),
                ((Number) row.get("user_id")).longValue());
    }

    // CA2

    @Test
    void admin_listsTheOpenTicketsNewestFirst_andFiltersByStatus() throws Exception {
        String admin = loginAsAdmin();
        String primeiro = newAddress(), segundo = newAddress(), terceiro = newAddress();
        send(null, "DUVIDA", primeiro).andExpect(status().isOk());
        now = now.plus(Duration.ofMinutes(5));
        send(null, "PROBLEMA", segundo).andExpect(status().isOk());
        now = now.plus(Duration.ofMinutes(5));
        send(null, "OUTRO", terceiro).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE support_tickets SET status = 'RESOLVED' WHERE reply_to = ?", segundo);

        mockMvc.perform(get("/api/v1/admin/support/tickets").param("status", "OPEN").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].replyTo").value(terceiro))
                .andExpect(jsonPath("$.content[1].replyTo").value(primeiro))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].emailSent").value(true))
                .andExpect(jsonPath("$.content[0].reference").value(org.hamcrest.Matchers.matchesPattern(REFERENCE)))
                .andExpect(jsonPath("$.content[0].message").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/support/tickets").param("status", "RESOLVED").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].replyTo").value(segundo));

        mockMvc.perform(get("/api/v1/admin/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].replyTo").value(terceiro));
    }

    @Test
    void admin_opensATicket_withTheMessageAndTheAccountName() throws Exception {
        String admin = loginAsAdmin();
        String token = loginAs(newAddress(), "Maria Silva");
        send(token, "SUGESTAO", null).andExpect(status().isOk());
        long id = onlyTicketId();

        mockMvc.perform(get("/api/v1/admin/support/tickets/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.message").value(MESSAGE))
                .andExpect(jsonPath("$.accountName").value("Maria Silva"))
                .andExpect(jsonPath("$.statusChangedAt").doesNotExist());
    }

    // CA3

    @Test
    void admin_changesTheStatus_recordsWhen_andCanReopen() throws Exception {
        String admin = loginAsAdmin();
        send(null, "PROBLEMA", sender).andExpect(status().isOk());
        long id = onlyTicketId();

        now = now.plus(Duration.ofHours(1));
        patchStatus(admin, id, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.statusChangedAt").value(now.toString()));
        assertEquals("IN_PROGRESS", onlyTicket().get("status"));

        patchStatus(admin, id, "RESOLVED").andExpect(status().isOk());
        patchStatus(admin, id, "OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/v1/admin/support/tickets").param("status", "OPEN").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void sameStatus_isAccepted_withoutChangingWhen() throws Exception {
        String admin = loginAsAdmin();
        send(null, "PROBLEMA", sender).andExpect(status().isOk());
        long id = onlyTicketId();

        patchStatus(admin, id, "OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.statusChangedAt").doesNotExist());
        assertNull(onlyTicket().get("status_changed_at"));
    }

    // CA4

    @Test
    void commonAccount_getsNotFound_onTheThreeRoutes() throws Exception {
        send(null, "PROBLEMA", sender).andExpect(status().isOk());
        long id = onlyTicketId();
        String token = loginAs(newAddress(), "Maria Silva");

        mockMvc.perform(get("/api/v1/admin/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/support/tickets/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        patchStatus(token, id, "RESOLVED").andExpect(status().isNotFound());

        assertEquals("OPEN", onlyTicket().get("status"));
    }

    @Test
    void withoutLogin_getsUnauthorized_onTheThreeRoutes() throws Exception {
        send(null, "PROBLEMA", sender).andExpect(status().isOk());
        long id = onlyTicketId();

        mockMvc.perform(get("/api/v1/admin/support/tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/support/tickets/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/admin/support/tickets/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profile_tellsWhoIsAdmin_ignoringCase() throws Exception {
        String admin = loginAsAdmin();
        String common = loginAs(newAddress(), "Maria Silva");

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + common))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void secondEmailOfTheList_isAlsoAdmin() throws Exception {
        String token = loginAs("second.admin@example.test", "Segunda Admin");

        mockMvc.perform(get("/api/v1/admin/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    // CA7

    @Test
    void emailFailure_keepsTheTicket_markedAsNotSent() throws Exception {
        String admin = loginAsAdmin();
        emailSender.failFromNowOn(true);

        send(null, "PROBLEMA", sender)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern(REFERENCE)));

        Map<String, Object> row = onlyTicket();
        assertEquals("OPEN", row.get("status"));
        assertEquals(false, row.get("email_sent"));

        mockMvc.perform(get("/api/v1/admin/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.content[0].emailSent").value(false));
    }

    // Bordas

    @Test
    void invalidStatus_isBadRequest_andUnknownTicketIsNotFound() throws Exception {
        String admin = loginAsAdmin();
        send(null, "PROBLEMA", sender).andExpect(status().isOk());
        long id = onlyTicketId();

        patchStatus(admin, id, "ARQUIVADO").andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/admin/support/tickets/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").isNotEmpty());
        assertEquals("OPEN", onlyTicket().get("status"));

        patchStatus(admin, id + 1000, "RESOLVED").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/support/tickets/" + (id + 1000)).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void htmlInTheMessage_isReturnedAsTyped() throws Exception {
        String admin = loginAsAdmin();
        String html = "<script>alert(1)</script> & ação";
        mockMvc.perform(post("/api/v1/support/tickets")
                        .with(r -> {
                            r.setRemoteAddr(uniqueIp());
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("OUTRO", html, sender))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/support/tickets/" + onlyTicketId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.message").value(html));
    }

    // Helpers

    private Map<String, Object> onlyTicket() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM support_tickets");
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private long onlyTicketId() {
        return ((Number) onlyTicket().get("id")).longValue();
    }

    private EmailMessage onlyEmail() {
        assertEquals(1, emailSender.sent().size());
        return emailSender.sent().get(0);
    }

    private Map<String, Object> body(String category, String message, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("category", category);
        body.put("message", message);
        body.put("email", email);
        return body;
    }

    private ResultActions send(String token, String category, String email) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/support/tickets")
                .with(r -> {
                    r.setRemoteAddr(uniqueIp());
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body(category, MESSAGE, email)));
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions patchStatus(String token, long id, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/support/tickets/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private String loginAsAdmin() throws Exception {
        return loginAs(ADMIN_EMAIL, "Admin");
    }

    /** O cadastro de um e-mail que já existe não falha, então a conta fixa do administrador serve para todos os testes. */
    private String loginAs(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, name))));
        markVerified(email);
        emailSender.clear();

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private String newAddress() {
        return "ticket-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "10.50." + (n / 250) + "." + (n % 250 + 1);
    }
}
