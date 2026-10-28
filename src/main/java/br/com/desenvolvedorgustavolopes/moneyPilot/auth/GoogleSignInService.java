package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.GoogleAccountConflictException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidCurrentPasswordException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleCredentialException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleTokenException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.UnauthenticatedRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class GoogleSignInService {

    private static final int AVATAR_URL_MAX = 2048;

    private final GoogleTokenVerifier verifier;
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final AuthenticatedUserProvider userProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final Clock clock;

    /** O e-mail é o da conta no app, que pode ser diferente do e-mail do Google. */
    public record SignInResult(AuthTokens tokens, String email) {
    }

    @Transactional
    public SignInResult signIn(String credential, String clientIp) {
        String ipKey = "login-ip:" + clientIp;
        attemptLimiter.check(ipKey, rateLimit.loginPerIp());
        attemptLimiter.record(ipKey, rateLimit.loginWindow());

        GoogleIdentity identity = normalized(verifier.verify(credential));

        // A conta é achada pelo sub: quem mudou o e-mail no Google continua entrando.
        User user = userRepository.findByGoogleSubject(identity.subject())
                .orElseGet(() -> accountForNewGoogle(identity));

        user.setGoogleEmail(identity.email());
        if (!user.isAvatarHidden()) {
            user.setAvatarUrl(pictureOf(identity));
        }
        userRepository.saveAndFlush(user);

        AuthTokens tokens = new AuthTokens(jwtService.generateToken(user.getId()), refreshTokenService.issue(user.getId()));
        return new SignInResult(tokens, user.getEmail());
    }

    /** Esse Google ainda não está vinculado a ninguém: vincula à conta com o mesmo e-mail, ou cria uma. */
    private User accountForNewGoogle(GoogleIdentity identity) {
        User user = userRepository.findUserByEmail(identity.email()).orElse(null);

        if (user == null) {
            user = new User();
            user.setEmail(identity.email());
            user.setName(displayName(identity));
            user.setCreatedAt(clock.instant());
            user.setEmailVerifiedAt(clock.instant());
        } else if (user.getGoogleSubject() != null) {
            throw new GoogleAccountConflictException("This e-mail belongs to an account linked to another Google account");
        } else if (user.getEmailVerifiedAt() == null) {
            // Alguém pode ter se cadastrado com o e-mail de outra pessoa: quem prova ser dono do e-mail
            // (o Google confirma) fica com a conta, e a senha escolhida no cadastro deixa de valer.
            user.setEmailVerifiedAt(clock.instant());
            user.setPasswordHash(null);
            verificationTokenRepository.findByUserId(user.getId()).ifPresent(verificationTokenRepository::delete);
        }

        user.setGoogleSubject(identity.subject());
        return user;
    }

    @Transactional
    public void link(String credential, String currentPassword) {
        User user = currentUser();
        String key = "password-change:" + user.getId();

        attemptLimiter.check(key, rateLimit.passwordChangeFailures());
        checkPassword(user, currentPassword, key);

        GoogleIdentity identity;
        try {
            identity = normalized(verifier.verify(credential));
        } catch (InvalidGoogleTokenException e) {
            throw new InvalidGoogleCredentialException();
        }

        if (user.getGoogleSubject() != null) {
            throw new GoogleAccountConflictException("This account is already linked to Google. Unlink it first");
        }

        if (userRepository.findByGoogleSubject(identity.subject()).isPresent()) {
            throw new GoogleAccountConflictException("This Google account is already linked to another account");
        }

        user.setGoogleSubject(identity.subject());
        user.setGoogleEmail(identity.email());
        user.setAvatarUrl(pictureOf(identity));
        userRepository.saveAndFlush(user);
        attemptLimiter.reset(key);
    }

    @Transactional
    public void unlink(String currentPassword) {
        User user = currentUser();
        String key = "password-change:" + user.getId();

        attemptLimiter.check(key, rateLimit.passwordChangeFailures());

        if (user.getGoogleSubject() == null) {
            throw new GoogleAccountConflictException("This account is not linked to Google");
        }

        if (!user.hasPassword()) {
            throw new GoogleAccountConflictException("Create a password before unlinking Google");
        }

        checkPassword(user, currentPassword, key);

        user.setGoogleSubject(null);
        user.setGoogleEmail(null);
        user.setAvatarUrl(null);
        user.setAvatarHidden(false);
        userRepository.saveAndFlush(user);
        attemptLimiter.reset(key);
    }

    @Transactional
    public void hideAvatar() {
        User user = currentUser();
        user.setAvatarHidden(true);
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public void useGoogleAvatar() {
        User user = currentUser();

        if (user.getGoogleSubject() == null) {
            throw new GoogleAccountConflictException("This account is not linked to Google");
        }

        user.setAvatarHidden(false);
        userRepository.saveAndFlush(user);
    }

    private void checkPassword(User user, String currentPassword, String key) {
        if (!user.hasPassword() || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            attemptLimiter.record(key, rateLimit.passwordChangeWindow());
            throw new InvalidCurrentPasswordException();
        }
    }

    private GoogleIdentity normalized(GoogleIdentity identity) {
        return new GoogleIdentity(
                identity.subject(), EmailAddress.normalize(identity.email()), identity.name(), identity.picture());
    }

    // A coluna guarda até 2048 caracteres; uma URL maior que isso não é uma foto que valha guardar.
    private String pictureOf(GoogleIdentity identity) {
        String picture = identity.picture();
        return picture == null || picture.isBlank() || picture.length() > AVATAR_URL_MAX ? null : picture;
    }

    private String displayName(GoogleIdentity identity) {
        String name = identity.name() == null ? "" : identity.name().strip();
        if (name.isEmpty()) {
            int at = identity.email().indexOf('@');
            name = at > 0 ? identity.email().substring(0, at) : identity.email();
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private User currentUser() {
        return userRepository.findById(userProvider.getCurrentUserId())
                .orElseThrow(UnauthenticatedRequestException::new);
    }
}
