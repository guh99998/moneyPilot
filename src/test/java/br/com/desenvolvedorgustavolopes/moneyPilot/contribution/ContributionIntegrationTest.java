package br.com.desenvolvedorgustavolopes.moneyPilot.contribution;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.pix.key=  00000000-0000-4000-8000-000000000000  ",
        "app.pix.qr-file=src/test/resources/pix-qr-test.png"
})
class ContributionIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // CA1

    @Test
    void profile_saysTheContributionIsEnabled() throws Exception {
        String token = loginAs(newAddress());

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contributionEnabled").value(true));
    }

    // CA2

    @Test
    void pix_returnsTheKeyWithoutSpacesAndTheQrCodeAsDataUri() throws Exception {
        String token = loginAs(newAddress());

        mockMvc.perform(get("/api/v1/contribution/pix").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("00000000-0000-4000-8000-000000000000"))
                .andExpect(jsonPath("$.qrImage").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")));
    }

    @Test
    void pix_withoutLogin_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/contribution/pix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("00000000-0000-4000-8000-000000000000"));
    }

    // helpers

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
        return "pix-" + UUID.randomUUID() + "@example.com";
    }
}
