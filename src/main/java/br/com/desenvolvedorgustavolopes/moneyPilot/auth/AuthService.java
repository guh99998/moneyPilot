package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

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

    public AuthTokens login(LoginRequest request) {
        User user = repository.findUserByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String accessToken = jwtService.generateToken(user.getEmail());

        String refreshToken = refreshTokenService.issue(user.getId());

        return new AuthTokens(accessToken, refreshToken);
    }

    public AuthTokens refresh(String rawToken) {
        RefreshResult result = refreshTokenService.rotate(rawToken);
        User user = repository.findById(result.userId()).orElseThrow(() -> new BadCredentialsException("Bad Credentials"));
        String accessToken = jwtService.generateToken(user.getEmail());
        return new AuthTokens(accessToken, result.refreshToken());
    }

    public void logout(String rawToken) {
        refreshTokenService.revoke(rawToken);
    }
}
