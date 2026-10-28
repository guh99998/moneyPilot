package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.RecordingEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O bcrypt só usa os primeiros 72 bytes da senha e recusa entradas maiores. Senha nova vai até 64 caracteres
 * (e 72 bytes, por causa de acentos e emoji); senha informada para conferir, se passar disso, só não confere.
 */
class LongPasswordIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "minha frase secreta";
    private static final String TOO_LONG = "a".repeat(65);
    // 64 caracteres, sem repetição nem sequência.
    private static final String LONGEST = "uma frase longa que vai até o limite de sessenta e quatro letras";
    // 20 caracteres, 80 bytes em UTF-8.
    private static final String TOO_MANY_BYTES = "🔒".repeat(20);
    private static final Pattern TOKEN = Pattern.compile("token=(\\S+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingEmailSender emailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        emailSender.clear();
    }

    @Test
    void register_withMoreThan64Characters_isRefusedWithFieldError() throws Exception {
        register(newAddress(), TOO_LONG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("A senha precisa ter de 10 a 64 caracteres."));
    }

    @Test
    void register_withMoreThan72Bytes_isRefusedWithFieldError() throws Exception {
        register(newAddress(), TOO_MANY_BYTES)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_withExactly64Characters_works_andLogsIn() throws Exception {
        String email = newAddress();

        register(email, LONGEST).andExpect(status().isAccepted());
        markVerified(email);

        login(email, LONGEST).andExpect(status().isOk());
    }

    @Test
    void register_withFewerThan10Characters_isRefusedWithTheSameMessage() throws Exception {
        register(newAddress(), "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("A senha precisa ter de 10 a 64 caracteres."));
    }

    @Test
    void login_withAPasswordLongerThanBcryptAccepts_isJustWrong() throws Exception {
        String email = registerVerified();

        login(email, "a".repeat(100)).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withATooLongNewPassword_isRefusedWithFieldError() throws Exception {
        String token = accessToken(registerVerified());

        changePassword(token, PASSWORD, TOO_LONG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    void changePassword_withATooLongCurrentPassword_isJustWrong() throws Exception {
        String token = accessToken(registerVerified());

        changePassword(token, "a".repeat(100), "nova-senha-678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
    }

    @Test
    void emailChange_withATooLongCurrentPassword_isJustWrong() throws Exception {
        String token = accessToken(registerVerified());

        mockMvc.perform(post("/api/v1/profile/email")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("newEmail", newAddress(), "currentPassword", "a".repeat(100)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());
    }

    @Test
    void reset_withATooLongNewPassword_isRefusedWithFieldError() throws Exception {
        String email = registerVerified();
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", lastTokenSentTo(email), "newPassword", TOO_MANY_BYTES))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    private String registerVerified() throws Exception {
        String email = newAddress();
        register(email, PASSWORD).andExpect(status().isAccepted());
        markVerified(email);
        emailSender.clear();
        return email;
    }

    private ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Maria Silva"))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private String accessToken(String email) throws Exception {
        String body = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions changePassword(String token, String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
    }

    private String lastTokenSentTo(String email) {
        List<EmailMessage> sent = emailSender.sent().stream()
                .filter(message -> message.to().equals(email))
                .toList();
        Matcher matcher = TOKEN.matcher(sent.get(sent.size() - 1).text());
        assertTrue(matcher.find(), "no link in the last e-mail to " + email);
        return matcher.group(1);
    }

    private String newAddress() {
        return "longa-" + UUID.randomUUID() + "@example.com";
    }
}
