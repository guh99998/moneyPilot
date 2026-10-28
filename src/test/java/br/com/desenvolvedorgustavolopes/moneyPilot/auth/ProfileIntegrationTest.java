package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String PASSWORD = "senha12345";
    private static final String NEW_PASSWORD = "outra-senha-9876";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Instant now;

    private record Session(String accessToken, String refreshToken) {
    }

    @BeforeEach
    void startClock() {
        now = Instant.parse("2026-10-27T15:00:00Z");
        when(clock.getZone()).thenReturn(SAO_PAULO);
        when(clock.instant()).thenAnswer(invocation -> now);
    }

    // CA1

    @Test
    void getProfile_returnsTheOwnNameEmailAndCreationDate() throws Exception {
        String email = registerNewUser("Maria Silva");
        Session session = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void getProfile_eachUserSeesOnlyTheirOwn() throws Exception {
        String emailA = registerNewUser("Ana");
        String emailB = registerNewUser("Bruno");
        Session sessionA = login(emailA, PASSWORD);
        Session sessionB = login(emailB, PASSWORD);

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(sessionA)))
                .andExpect(jsonPath("$.email").value(emailA));
        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(sessionB)))
                .andExpect(jsonPath("$.email").value(emailB));
    }

    @Test
    void profile_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    // CA2

    @Test
    void updateName_trimsAndPersists() throws Exception {
        Session session = login(registerNewUser("Nome Errado"), PASSWORD);

        updateName(session, "  Novo Nome  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo Nome"));

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(jsonPath("$.name").value("Novo Nome"));
    }

    @Test
    void updateName_doesNotChangeAnotherUsersName() throws Exception {
        Session sessionA = login(registerNewUser("Ana"), PASSWORD);
        Session sessionB = login(registerNewUser("Bruno"), PASSWORD);

        updateName(sessionA, "Ana Maria").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(sessionB)))
                .andExpect(jsonPath("$.name").value("Bruno"));
    }

    // CA3

    @Test
    void updateName_emptyOrBlank_isRefusedWithFieldError() throws Exception {
        Session session = login(registerNewUser("Maria"), PASSWORD);

        updateName(session, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
        updateName(session, "   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());

        mockMvc.perform(get("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, bearer(session)))
                .andExpect(jsonPath("$.name").value("Maria"));
    }

    @Test
    void updateName_limitIs255Characters() throws Exception {
        Session session = login(registerNewUser("Maria"), PASSWORD);

        updateName(session, "a".repeat(256)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        updateName(session, "a".repeat(255)).andExpect(status().isOk());
    }

    // CA4

    @Test
    void changePassword_nextLoginWorksWithTheNewPasswordOnly() throws Exception {
        String email = registerNewUser("Maria");
        Session session = login(email, PASSWORD);

        changePassword(session, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        loginRequest(email, NEW_PASSWORD).andExpect(status().isOk());
        loginRequest(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_newPasswordShorterThanEight_isRefused() throws Exception {
        String email = registerNewUser("Maria");
        Session session = login(email, PASSWORD);

        changePassword(session, PASSWORD, "curta12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        loginRequest(email, PASSWORD).andExpect(status().isOk());
    }

    // CA5

    @Test
    void changePassword_wrongCurrentPassword_changesNothingAndShowsFieldError() throws Exception {
        String email = registerNewUser("Maria");
        Session session = login(email, PASSWORD);

        changePassword(session, "senha-errada", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());

        loginRequest(email, PASSWORD).andExpect(status().isOk());
        loginRequest(email, NEW_PASSWORD).andExpect(status().isUnauthorized());
    }

    // CA6

    @Test
    void changePassword_endsOtherSessionsAndKeepsTheOneThatChangedIt() throws Exception {
        String email = registerNewUser("Maria");
        Session phone = login(email, PASSWORD);
        Session laptop = login(email, PASSWORD);

        changePassword(laptop, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        refresh(phone).andExpect(status().isUnauthorized());
        refresh(laptop).andExpect(status().isOk());
    }

    @Test
    void changePassword_doesNotTouchAnotherUsersSessions() throws Exception {
        Session maria = login(registerNewUser("Maria"), PASSWORD);
        Session other = login(registerNewUser("Outra"), PASSWORD);

        changePassword(maria, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        refresh(other).andExpect(status().isOk());
    }

    // Bordas

    @Test
    void changePassword_sameAsCurrent_isRefusedAndOtherSessionsSurvive() throws Exception {
        String email = registerNewUser("Maria");
        Session phone = login(email, PASSWORD);
        Session laptop = login(email, PASSWORD);

        changePassword(laptop, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        refresh(phone).andExpect(status().isOk());
        loginRequest(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changePassword_sixthWrongAttempt_isRefusedEvenWithTheRightPassword() throws Exception {
        String email = registerNewUser("Maria");
        Session session = login(email, PASSWORD);

        for (int i = 0; i < 5; i++) {
            changePassword(session, "senha-errada", NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        changePassword(session, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
        loginRequest(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changePassword_afterTheWindowPasses_isProcessedNormally() throws Exception {
        String email = registerNewUser("Maria");
        Session session = login(email, PASSWORD);
        for (int i = 0; i < 5; i++) {
            changePassword(session, "senha-errada", NEW_PASSWORD);
        }
        changePassword(session, PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());

        now = now.plusSeconds(15 * 60);

        changePassword(session, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void changePassword_rightPasswordResetsTheFailureCounter() throws Exception {
        String email = registerNewUser("Maria");
        Session session = login(email, PASSWORD);

        for (int i = 0; i < 4; i++) {
            changePassword(session, "senha-errada", "qualquer-coisa-1").andExpect(status().isBadRequest());
        }
        changePassword(session, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        for (int i = 0; i < 5; i++) {
            changePassword(session, "senha-errada", PASSWORD).andExpect(status().isBadRequest());
        }
    }

    // helpers

    private ResultActions updateName(Session session, String name) throws Exception {
        return mockMvc.perform(put("/api/v1/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))));
    }

    private ResultActions changePassword(Session session, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(session))
                .cookie(new Cookie(COOKIE_NAME, session.refreshToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(current, next))));
    }

    private ResultActions refresh(Session session) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, session.refreshToken())));
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private String registerNewUser(String name) throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, name))))
                .andExpect(status().isAccepted());
        markVerified(email);
        return email;
    }

    private Session login(String email, String password) throws Exception {
        MvcResult result = loginRequest(email, password).andExpect(status().isOk()).andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        String setCookie = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow();
        String refreshToken = setCookie.substring((COOKIE_NAME + "=").length(), setCookie.indexOf(';'));
        return new Session(accessToken, refreshToken);
    }

    private String bearer(Session session) {
        return "Bearer " + session.accessToken();
    }
}
