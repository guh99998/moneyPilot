package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 011: sessão para clientes fora do navegador (refresh token no corpo). */
class ApiSessionIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Session(String accessToken, String refreshToken) {
    }

    @Test
    void login_withRefreshTokenInBody_returnsThreeFields_andNoCookie() throws Exception {
        String email = registerNewUser();

        MvcResult result = bodyLogin(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(jwtService.expirationSeconds()))
                .andReturn();

        assertFalse(result.getResponse().containsHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refresh_withBody_rotates_andTheOldTokenStopsWorking() throws Exception {
        Session first = login(registerNewUser());

        MvcResult refreshed = refreshWithBody(first.refreshToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(jwtService.expirationSeconds()))
                .andReturn();
        assertFalse(refreshed.getResponse().containsHeader(HttpHeaders.SET_COOKIE));
        String second = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.refreshToken");
        assertFalse(second.equals(first.refreshToken()));

        refreshWithBody(first.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_reusedThroughTheBody_revokesEverySessionOfTheUser() throws Exception {
        String email = registerNewUser();
        Session first = login(email);
        Session other = login(email);

        String second = JsonPath.read(
                refreshWithBody(first.refreshToken()).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        refreshWithBody(first.refreshToken()).andExpect(status().isUnauthorized());
        refreshWithBody(second).andExpect(status().isUnauthorized());
        refreshWithBody(other.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withCookieAndBody_usesTheBody_andSendsNoCookie() throws Exception {
        Session forBody = login(registerNewUser());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "not-a-real-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", forBody.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        assertFalse(result.getResponse().containsHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void logout_withBody_revokesTheToken_andSendsNoCookie() throws Exception {
        Session session = login(registerNewUser());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", session.refreshToken()))))
                .andExpect(status().isNoContent())
                .andReturn();
        assertFalse(result.getResponse().containsHeader(HttpHeaders.SET_COOKIE));

        refreshWithBody(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void login_googleOnlyAccount_inBodyMode_isUnauthorized() throws Exception {
        String email = registerNewUser();
        jdbc.update("UPDATE users SET password_hash = NULL WHERE email = ?", email);

        bodyLogin(email).andExpect(status().isUnauthorized());
    }

    @Test
    void browserFlow_isUnchanged_cookieOnly_andRefreshWithoutBodyOrContentTypeWorks() throws Exception {
        String email = registerNewUser();

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String body = login.getResponse().getContentAsString();
        assertFalse(body.contains("refreshToken"), body);
        assertFalse(body.contains("expiresIn"), body);

        String header = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String cookie = header.substring("refresh_token=".length(), header.indexOf(';'));

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", cookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        assertEquals(1, refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE).size());
        assertFalse(refreshed.getResponse().getContentAsString().contains("refresh"));
    }

    private ResultActions bodyLogin(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email, "password", PASSWORD, "refreshTokenInBody", true))));
    }

    private ResultActions refreshWithBody(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", token))));
    }

    private Session login(String email) throws Exception {
        MvcResult result = bodyLogin(email).andExpect(status().isOk()).andReturn();
        String json = result.getResponse().getContentAsString();
        return new Session(JsonPath.read(json, "$.token"), JsonPath.read(json, "$.refreshToken"));
    }

    private String registerNewUser() throws Exception {
        String email = "api-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Api User"))))
                .andExpect(status().isAccepted());
        markVerified(email);
        return email;
    }
}
