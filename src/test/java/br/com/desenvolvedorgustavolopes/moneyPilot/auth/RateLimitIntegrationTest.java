package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.rate-limit.login-per-ip=10",
        "app.rate-limit.login-failures-per-email=3",
        "app.rate-limit.register-per-ip=3",
        "server.forward-headers-strategy=framework"
})
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "senha12345";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Instant now;

    @BeforeEach
    void startClock() {
        now = Instant.parse("2026-10-27T15:00:00Z");
        when(clock.getZone()).thenReturn(SAO_PAULO);
        when(clock.instant()).thenAnswer(invocation -> now);
    }

    @Test
    void login_afterThreeWrongPasswords_refusesEvenTheRightPassword() throws Exception {
        String ip = uniqueIp();
        String email = registeredEmail();

        wrongLogin(email, ip).andExpect(status().isUnauthorized());
        wrongLogin(email, ip).andExpect(status().isUnauthorized());
        wrongLogin(email, ip).andExpect(status().isUnauthorized());

        login(email, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many attempts. Try again in 15 minutes."));
    }

    @Test
    void login_failureLockAppliesToTheEmailFromAnyIp() throws Exception {
        String email = registeredEmail();

        wrongLogin(email, uniqueIp()).andExpect(status().isUnauthorized());
        wrongLogin(email, uniqueIp()).andExpect(status().isUnauthorized());
        wrongLogin(email, uniqueIp()).andExpect(status().isUnauthorized());

        login(email.toUpperCase(), PASSWORD, uniqueIp()).andExpect(status().isTooManyRequests());
    }

    @Test
    void login_afterTheWindowPasses_isProcessedNormally() throws Exception {
        String ip = uniqueIp();
        String email = registeredEmail();
        for (int i = 0; i < 3; i++) {
            wrongLogin(email, ip);
        }
        login(email, PASSWORD, ip).andExpect(status().isTooManyRequests());

        now = now.plusSeconds(15 * 60);

        login(email, PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void login_twoMistakesThenSuccess_isNeverBlockedAndResetsTheEmailCounter() throws Exception {
        String ip = uniqueIp();
        String email = registeredEmail();

        wrongLogin(email, ip).andExpect(status().isUnauthorized());
        wrongLogin(email, ip).andExpect(status().isUnauthorized());
        login(email, PASSWORD, ip).andExpect(status().isOk());

        wrongLogin(email, ip).andExpect(status().isUnauthorized());
        wrongLogin(email, ip).andExpect(status().isUnauthorized());
        login(email, PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void login_eleventhAttemptFromTheSameIpWithDifferentEmails_isRefused() throws Exception {
        String ip = uniqueIp();

        for (int i = 0; i < 10; i++) {
            wrongLogin(uniqueEmail(), ip).andExpect(status().isUnauthorized());
        }

        wrongLogin(uniqueEmail(), ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void register_fourthRegistrationFromTheSameIp_isRefusedButAnotherIpPasses() throws Exception {
        String ip = uniqueIp();

        for (int i = 0; i < 3; i++) {
            register(uniqueEmail(), ip).andExpect(status().isAccepted());
        }

        register(uniqueEmail(), ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        register(uniqueEmail(), uniqueIp()).andExpect(status().isAccepted());
    }

    @Test
    void register_duplicateEmailCountsTowardTheLimit() throws Exception {
        String ip = uniqueIp();
        String email = uniqueEmail();

        register(email, ip).andExpect(status().isAccepted());
        register(email, ip).andExpect(status().isAccepted());
        register(email, ip).andExpect(status().isAccepted());

        register(uniqueEmail(), ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void register_afterTheWindowPasses_isProcessedNormally() throws Exception {
        String ip = uniqueIp();
        for (int i = 0; i < 3; i++) {
            register(uniqueEmail(), ip).andExpect(status().isAccepted());
        }
        register(uniqueEmail(), ip).andExpect(status().isTooManyRequests());

        now = now.plusSeconds(60 * 60);

        register(uniqueEmail(), ip).andExpect(status().isAccepted());
    }

    @Test
    void forwardedFor_countsPerVisitorIpBehindTheProxy() throws Exception {
        String visitorA = uniqueIp();
        String visitorB = uniqueIp();

        for (int i = 0; i < 3; i++) {
            registerForwarded(uniqueEmail(), visitorA).andExpect(status().isAccepted());
        }

        registerForwarded(uniqueEmail(), visitorA).andExpect(status().isTooManyRequests());
        registerForwarded(uniqueEmail(), visitorB).andExpect(status().isAccepted());
    }

    /** Each registration comes from its own IP so the per-IP register limit does not interfere. */
    private String registeredEmail() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueIp()).andExpect(status().isAccepted());
        markVerified(email);
        return email;
    }

    private ResultActions wrongLogin(String email, String ip) throws Exception {
        return login(email, "senha-errada", ip);
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private ResultActions register(String email, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Test User"))));
    }

    private ResultActions registerForwarded(String email, String visitorIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .with(request -> {
                    request.setRemoteAddr("10.0.0.1");
                    return request;
                })
                .header("X-Forwarded-For", visitorIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Test User"))));
    }

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "198.51." + (n / 250) + "." + (n % 250 + 1);
    }
}
