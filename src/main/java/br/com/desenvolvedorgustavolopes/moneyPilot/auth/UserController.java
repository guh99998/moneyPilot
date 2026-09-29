package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;
    private final AuthService authService;

    @Value("${app.cookie-secure}")
    private boolean cookieSecure;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDTO createUser(@Valid @RequestBody RegisterRequest request) {
        return service.createUser(request);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthTokens authTokens = authService.login(request);
        ResponseCookie cookie = this.buildRefreshCookie(authTokens.refreshToken(), RefreshTokenService.REFRESH_TTL);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new AuthResponse(authTokens.accessToken());
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken, HttpServletResponse response) {
        AuthTokens authTokens = authService.refresh(refreshToken);
        ResponseCookie cookie = this.buildRefreshCookie(authTokens.refreshToken(), RefreshTokenService.REFRESH_TTL);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new AuthResponse(authTokens.accessToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = "refresh_token", required = false) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken);
        ResponseCookie cookie = this.buildRefreshCookie("", Duration.ZERO);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

}
