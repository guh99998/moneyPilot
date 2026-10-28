package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final RefreshCookies refreshCookies;
    private final JwtService jwtService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void createUser(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        service.createUser(request, httpRequest.getRemoteAddr());
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthTokens authTokens = authService.login(request, httpRequest.getRemoteAddr());
        return respond(authTokens, request.wantsRefreshTokenInBody(), response);
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse refresh(@CookieValue(name = "refresh_token", required = false) String cookieToken,
                                @RequestBody(required = false) RefreshRequest body, HttpServletResponse response) {
        String bodyToken = bodyToken(body);
        AuthTokens authTokens = authService.refresh(bodyToken != null ? bodyToken : cookieToken);
        return respond(authTokens, bodyToken != null, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = "refresh_token", required = false) String cookieToken,
                       @RequestBody(required = false) RefreshRequest body, HttpServletResponse response) {
        String bodyToken = bodyToken(body);
        authService.logout(bodyToken != null ? bodyToken : cookieToken);
        if (bodyToken == null) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.build("", Duration.ZERO).toString());
        }
    }

    // Modo corpo: o refresh token vai na resposta e nenhum cookie é enviado. Modo navegador: só o cookie.
    private AuthResponse respond(AuthTokens tokens, boolean inBody, HttpServletResponse response) {
        if (inBody) {
            return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), jwtService.expirationSeconds());
        }
        ResponseCookie cookie = refreshCookies.build(tokens.refreshToken(), RefreshTokenService.REFRESH_TTL);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new AuthResponse(tokens.accessToken());
    }

    private static String bodyToken(RefreshRequest body) {
        return body == null || body.refreshToken() == null || body.refreshToken().isBlank() ? null : body.refreshToken();
    }

}
