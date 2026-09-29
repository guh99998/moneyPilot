package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String PASSWORD = "senha12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Session(String accessToken, String refreshToken) {
    }

    @Test
    void login_setsHardenedCookie_andNeverReturnsTheRefreshTokenInTheBody() throws Exception {
        String email = registerNewUser();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String setCookie = setCookieHeader(result);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("Secure"), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
        assertTrue(setCookie.contains("Path=/api/v1/auth"), setCookie);
        assertTrue(setCookie.contains("Max-Age=" + RefreshTokenService.REFRESH_TTL.toSeconds()), setCookie);

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(cookieValue(result)), "the refresh token must never appear in the response body");
        assertFalse(body.toLowerCase().contains("refresh"), body);
    }

    @Test
    void refreshToken_isStoredOnlyAsSha256Hash() throws Exception {
        Session session = login(registerNewUser());

        assertTrue(refreshTokenRepository.findByTokenHash(session.refreshToken()).isEmpty(),
                "the raw token must not be what is stored");
        assertTrue(refreshTokenRepository.findByTokenHash(sha256Hex(session.refreshToken())).isPresent());
    }

    @Test
    void refresh_rotatesTheToken_issuesUsableAccessToken_andRevokesTheOldOne() throws Exception {
        Session first = login(registerNewUser());

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, first.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String newRefreshToken = cookieValue(refreshed);
        String newAccessToken = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.token");

        assertNotEquals(first.refreshToken(), newRefreshToken);

        mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk());

        RefreshToken old = refreshTokenRepository.findByTokenHash(sha256Hex(first.refreshToken())).orElseThrow();
        RefreshToken current = refreshTokenRepository.findByTokenHash(sha256Hex(newRefreshToken)).orElseThrow();
        assertNotNull(old.getRevokedAt());
        assertNull(current.getRevokedAt());
    }

    @Test
    void reusingARotatedToken_revokesEverySessionOfThatUser_butNotOtherUsers() throws Exception {
        String email = registerNewUser();
        Session sessionA = login(email);
        Session sessionB = login(email);
        Session otherUser = login(registerNewUser());

        // A rotates legitimately...
        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, sessionA.refreshToken())))
                .andExpect(status().isOk())
                .andReturn();
        String rotatedToken = cookieValue(rotated);

        // ...and then the old value shows up again: theft or replay
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, sessionA.refreshToken())))
                .andExpect(status().isUnauthorized());

        // the revocation must survive the 401 (no rollback), and reach the user's other sessions
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, rotatedToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, sessionB.refreshToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, otherUser.refreshToken())))
                .andExpect(status().isOk());
    }

    @Test
    void logout_revokesTheToken_andClearsTheCookie() throws Exception {
        Session session = login(registerNewUser());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE_NAME, session.refreshToken())))
                .andExpect(status().isNoContent())
                .andReturn();

        String setCookie = setCookieHeader(result);
        assertTrue(setCookie.contains("Max-Age=0"), setCookie);
        assertEquals("", cookieValue(result));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingBlankUnknownAndExpiredTokens_areUnauthorized_notServerErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, " "))).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, "not-a-real-token"))).andExpect(status().isUnauthorized());

        Session session = login(registerNewUser());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(session.refreshToken())).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(stored);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutCookie_isAlwaysNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE_NAME, "not-a-real-token"))).andExpect(status().isNoContent());
    }

    @Test
    void simultaneousRefreshesWithTheSameToken_neverProduceTwoValidTokens() throws Exception {
        Session session = login(registerNewUser());
        int calls = 4;

        CountDownLatch ready = new CountDownLatch(calls);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(calls);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < calls; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, session.refreshToken())))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();

            int ok = 0;
            for (Future<Integer> result : results) {
                int code = result.get();
                assertTrue(code == 200 || code == 401, "unexpected status " + code);
                if (code == 200) ok++;
            }
            assertEquals(1, ok, "one token can be exchanged exactly once");
        } finally {
            pool.shutdownNow();
        }
    }

    private String registerNewUser() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, PASSWORD, "Refresh User"))))
                .andExpect(status().isCreated());
        return email;
    }

    private Session login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return new Session(accessToken, cookieValue(result));
    }

    private String setCookieHeader(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + COOKIE_NAME + " Set-Cookie header"));
    }

    private String cookieValue(MvcResult result) {
        String header = setCookieHeader(result);
        return header.substring((COOKIE_NAME + "=").length(), header.indexOf(';'));
    }

    private String sha256Hex(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
