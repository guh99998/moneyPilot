package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CA5: sem {@code ADMIN_EMAILS}, ninguém é administrador e o "Falar com o suporte" segue funcionando. */
@TestPropertySource(properties = "app.admin.emails=")
class AdminDisabledIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void withoutAdminEmails_nobodyIsAdmin_andSupportStillWorks() throws Exception {
        String email = "no-admin-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Ana"))))
                .andExpect(status().isAccepted());
        markVerified(email);
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.token");

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.admin").value(false));
        mockMvc.perform(get("/api/v1/admin/support/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/support/tickets")
                        .with(r -> {
                            r.setRemoteAddr("10.60.0.1");
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "DUVIDA", "message", "Como faço para exportar meus dados?", "email", email))))
                .andExpect(status().isOk());
    }
}
