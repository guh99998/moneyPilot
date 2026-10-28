package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.AppProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.EmailAlreadyInUseException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidCurrentPasswordException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidEmailChangeTokenException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.SameEmailException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.UnauthenticatedRequestException;
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
public class EmailChangeService {

    public static final Duration TOKEN_TTL = Duration.ofHours(24);

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final UserRepository userRepository;
    private final EmailChangeTokenRepository tokenRepository;
    private final AuthenticatedUserProvider userProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final OpaqueTokens opaqueTokens;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final Clock clock;

    /** E-mail aguardando confirmação, ou {@code null} se não há pedido válido. */
    public String pendingEmail(Long userId) {
        Instant now = clock.instant();
        return tokenRepository.findByUserId(userId)
                .filter(token -> token.getExpiresAt().isAfter(now))
                .map(EmailChangeToken::getNewEmail)
                .orElse(null);
    }

    @Transactional
    public void request(EmailChangeRequest request) {
        User user = userRepository.findById(userProvider.getCurrentUserId())
                .orElseThrow(UnauthenticatedRequestException::new);
        String passwordKey = "password-change:" + user.getId();
        String requestKey = "email-change:" + user.getId();

        attemptLimiter.check(passwordKey, rateLimit.passwordChangeFailures());
        attemptLimiter.check(requestKey, rateLimit.emailChangeRequests());

        if (!user.hasPassword() || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            attemptLimiter.record(passwordKey, rateLimit.passwordChangeWindow());
            throw new InvalidCurrentPasswordException();
        }

        String newEmail = request.newEmail();

        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new SameEmailException();
        }

        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new EmailAlreadyInUseException();
        }

        Instant now = clock.instant();
        String rawToken = opaqueTokens.generate();

        EmailChangeToken token = tokenRepository.findByUserId(user.getId()).orElseGet(EmailChangeToken::new);
        token.setUserId(user.getId());
        token.setNewEmail(newEmail);
        token.setTokenHash(opaqueTokens.hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(TOKEN_TTL));
        tokenRepository.saveAndFlush(token);

        // Se o envio falhar, a exceção desfaz a transação: nenhum pedido fica gravado.
        emailSender.send(confirmationMessage(newEmail, user.getName(), rawToken));
        attemptLimiter.record(requestKey, rateLimit.emailChangeWindow());
    }

    @Transactional
    public void confirm(String rawToken, String currentRefreshToken) {
        EmailChangeToken token = tokenRepository.findByTokenHash(opaqueTokens.hash(rawToken))
                .orElseThrow(InvalidEmailChangeTokenException::new);

        if (!token.getExpiresAt().isAfter(clock.instant())) {
            discard(token);
            throw new InvalidEmailChangeTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidEmailChangeTokenException::new);

        if (userRepository.existsByEmailIgnoreCase(token.getNewEmail())) {
            discard(token);
            throw new EmailAlreadyInUseException();
        }

        String oldEmail = user.getEmail();
        user.setEmail(token.getNewEmail());
        userRepository.saveAndFlush(user);
        discard(token);

        refreshTokenService.endAllExcept(user.getId(), currentRefreshToken);

        notifyOldEmail(oldEmail, user.getName(), user.getEmail());
    }

    private void discard(EmailChangeToken token) {
        tokenRepository.delete(token);
        tokenRepository.flush();
    }

    private void notifyOldEmail(String oldEmail, String name, String newEmail) {
        String when = DATE_TIME.format(clock.instant().atZone(clock.getZone()));
        String text = """
                Olá, %s!

                O e-mail da sua conta no Money Pilot foi alterado em %s.

                Novo e-mail: %s

                A partir de agora, o acesso e a recuperação de senha usam o novo endereço. As outras sessões abertas foram encerradas.
                """.formatted(name, when, newEmail);

        try {
            emailSender.send(new EmailMessage(oldEmail, "O e-mail da sua conta foi alterado", text));
        } catch (RuntimeException e) {
            log.error("Could not send the e-mail change notice to the previous address: {}", e.getClass().getSimpleName());
        }
    }

    private EmailMessage confirmationMessage(String newEmail, String name, String rawToken) {
        String link = appProperties.publicUrl() + "/#/confirmar-email?token=" + rawToken;
        String text = """
                Olá, %s!

                Recebemos um pedido para usar este endereço como o e-mail da sua conta no Money Pilot.

                Para confirmar a troca, abra o link abaixo (vale por 24 horas e só pode ser usado uma vez):

                %s

                Até a confirmação, o e-mail atual continua valendo. Se você não pediu essa troca, ignore esta mensagem.
                """.formatted(name, link);

        return new EmailMessage(newEmail, "Confirme o novo e-mail da sua conta", text);
    }
}
