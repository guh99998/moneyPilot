package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.EmailNotVerifiedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;

    public AuthTokens login(LoginRequest request, String clientIp) {
        String ipKey = "login-ip:" + clientIp;
        String emailKey = "login-email:" + request.email();

        attemptLimiter.check(ipKey, rateLimit.loginPerIp());
        attemptLimiter.check(emailKey, rateLimit.loginFailuresPerEmail());
        attemptLimiter.record(ipKey, rateLimit.loginWindow());

        User user = repository.findUserByEmail(request.email()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            attemptLimiter.record(emailKey, rateLimit.loginWindow());
            throw new BadCredentialsException("Invalid credentials");
        }

        attemptLimiter.reset(emailKey);

        // Só quem sabe a senha chega aqui, então o 403 não revela se o e-mail tem conta.
        if (user.getEmailVerifiedAt() == null) {
            throw new EmailNotVerifiedException();
        }

        String accessToken = jwtService.generateToken(user.getId());

        String refreshToken = refreshTokenService.issue(user.getId());

        return new AuthTokens(accessToken, refreshToken);
    }

    public AuthTokens refresh(String rawToken) {
        RefreshResult result = refreshTokenService.rotate(rawToken);
        User user = repository.findById(result.userId()).orElseThrow(() -> new BadCredentialsException("Bad Credentials"));
        String accessToken = jwtService.generateToken(user.getId());
        return new AuthTokens(accessToken, result.refreshToken());
    }

    public void logout(String rawToken) {
        refreshTokenService.revoke(rawToken);
    }
}
