package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** O cookie do refresh token, igual para todo caminho que abre sessão (senha e Google). */
@Component
public class RefreshCookies {

    private final boolean secure;

    public RefreshCookies(@Value("${app.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
