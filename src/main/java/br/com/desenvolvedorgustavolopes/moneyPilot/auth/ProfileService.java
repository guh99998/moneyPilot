package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.AdminProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.config.PixProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidCurrentPasswordException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.SamePasswordException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.UnauthenticatedRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository repository;
    private final AuthenticatedUserProvider userProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final EmailChangeService emailChangeService;
    private final PasswordPolicy passwordPolicy;
    private final AdminProperties adminProperties;
    private final PixProperties pixProperties;

    public ProfileResponse getProfile() {
        User user = currentUser();
        return new ProfileResponse(user, emailChangeService.pendingEmail(user.getId()), adminProperties.isAdminEmail(user.getEmail()), pixProperties.enabled());
    }

    @Transactional
    public ProfileResponse updateName(UpdateProfileRequest request) {
        User user = currentUser();
        user.setName(request.name());
        User saved = repository.save(user);
        return new ProfileResponse(saved, emailChangeService.pendingEmail(saved.getId()), adminProperties.isAdminEmail(saved.getEmail()), pixProperties.enabled());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, String currentRefreshToken) {
        User user = currentUser();
        String key = "password-change:" + user.getId();

        attemptLimiter.check(key, rateLimit.passwordChangeFailures());

        if (!user.hasPassword() || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            attemptLimiter.record(key, rateLimit.passwordChangeWindow());
            throw new InvalidCurrentPasswordException();
        }

        passwordPolicy.checkPersonal("newPassword", request.newPassword(), user.getEmail(), user.getName());

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new SamePasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        repository.saveAndFlush(user);
        attemptLimiter.reset(key);

        refreshTokenService.endAllExcept(user.getId(), currentRefreshToken);
    }

    private User currentUser() {
        return repository.findById(userProvider.getCurrentUserId())
                .orElseThrow(UnauthenticatedRequestException::new);
    }
}
