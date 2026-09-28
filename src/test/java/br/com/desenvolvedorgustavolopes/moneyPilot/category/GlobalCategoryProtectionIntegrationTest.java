package br.com.desenvolvedorgustavolopes.moneyPilot.category;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.LoginRequest;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalCategoryProtectionIntegrationTest extends AbstractIntegrationTest {

    private static final Long GLOBAL_CATEGORY_ID = 1L; // "Salário", seeded by V3, user_id = NULL

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void userCannotUpdateGlobalCategory() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(put("/api/v1/categories/{id}", GLOBAL_CATEGORY_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Hackeada", CategoryType.INCOME))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/categories/{id}", GLOBAL_CATEGORY_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Salário"));
    }

    @Test
    void userCannotDeleteGlobalCategory() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(delete("/api/v1/categories/{id}", GLOBAL_CATEGORY_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/categories/{id}", GLOBAL_CATEGORY_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
