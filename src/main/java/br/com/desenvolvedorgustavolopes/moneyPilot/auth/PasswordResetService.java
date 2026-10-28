package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.AppProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidPasswordResetTokenException;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    public static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshTokenService refreshTokenService;
    private final OpaqueTokens opaqueTokens;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final Clock clock;

    /** Responde igual exista ou não a conta; falhas de envio só vão para o log. */
    @Transactional
    public void requestReset(String email, String clientIp) {
        String emailKey = "password-reset:" + email;
        String ipKey = "password-reset-ip:" + clientIp;

        attemptLimiter.check(ipKey, rateLimit.passwordResetPerIp());
        attemptLimiter.check(emailKey, rateLimit.passwordResetPerEmail());
        attemptLimiter.record(ipKey, rateLimit.passwordResetWindow());
        attemptLimiter.record(emailKey, rateLimit.passwordResetWindow());

        User user = userRepository.findUserByEmail(email).orElse(null);

        if (user == null) {
            return;
        }

        Instant now = clock.instant();
        String rawToken = opaqueTokens.generate();

        // Uma linha por usuário: pedir de novo substitui o hash, e o link anterior deixa de valer.
        PasswordResetToken token = tokenRepository.findByUserId(user.getId()).orElseGet(PasswordResetToken::new);
        token.setUserId(user.getId());
        token.setTokenHash(opaqueTokens.hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(TOKEN_TTL));
        tokenRepository.saveAndFlush(token);

        sendQuietly(resetMessage(user, rawToken));
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(opaqueTokens.hash(rawToken))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        Instant now = clock.instant();

        if (!token.getExpiresAt().isAfter(now)) {
            discard(token);
            throw new InvalidPasswordResetTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        // Senha recusada desfaz a transação: o link continua valendo para uma nova tentativa.
        passwordPolicy.checkPersonal("newPassword", newPassword, user.getEmail(), user.getName());

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Quem usou o link acabou de provar que é dono do e-mail.
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
        userRepository.saveAndFlush(user);
        discard(token);

        refreshTokenService.endAllExcept(user.getId(), null);
        attemptLimiter.reset("login-email:" + user.getEmail());

        sendQuietly(passwordChangedMessage(user));
    }

    private void discard(PasswordResetToken token) {
        tokenRepository.delete(token);
        tokenRepository.flush();
    }

    private void sendQuietly(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (RuntimeException e) {
            log.error("Could not send the password reset e-mail: {}", e.getClass().getSimpleName());
        }
    }

    private EmailMessage resetMessage(User user, String rawToken) {
        String link = appProperties.publicUrl() + "/#/redefinir-senha?token=" + rawToken;
        String text = """
                Olá, %s!

                Recebemos um pedido para redefinir a senha da sua conta no Money Pilot.

                Para criar uma senha nova, abra o link abaixo (vale por 30 minutos e só pode ser usado uma vez):

                %s

                Se você não fez esse pedido, ignore esta mensagem: a sua senha continua a mesma.
                """.formatted(user.getName(), link);

        return new EmailMessage(user.getEmail(), "Redefina a senha da sua conta", text);
    }

    private EmailMessage passwordChangedMessage(User user) {
        String when = DATE_TIME.format(clock.instant().atZone(clock.getZone()));
        String text = """
                Olá, %s!

                A senha da sua conta no Money Pilot foi redefinida em %s, e as sessões abertas foram encerradas.

                Se não foi você, peça uma nova redefinição de senha agora mesmo pela tela de login.
                """.formatted(user.getName(), when);

        return new EmailMessage(user.getEmail(), "A senha da sua conta foi alterada", text);
    }
}
