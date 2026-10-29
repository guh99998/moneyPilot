package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.AppProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidVerificationTokenException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.TooManyAttemptsException;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    public static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final OpaqueTokens opaqueTokens;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final Clock clock;

    /**
     * Gera um link novo para a conta, invalidando o anterior, e envia. Falha no envio propaga a exceção,
     * para quem chama desfazer a transação.
     */
    @Transactional
    public void sendLink(User user) {
        Instant now = clock.instant();
        String rawToken = opaqueTokens.generate();

        EmailVerificationToken token = tokenRepository.findByUserId(user.getId()).orElseGet(EmailVerificationToken::new);
        token.setUserId(user.getId());
        token.setTokenHash(opaqueTokens.hash(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(TOKEN_TTL));
        token.setUsedAt(null);
        tokenRepository.saveAndFlush(token);

        emailSender.send(verificationMessage(user, rawToken));
    }

    /** Avisa o dono de uma conta verificada que tentaram cadastrar o e-mail dele, no máximo uma vez por janela. */
    public void notifyRegisterAttempt(User user) {
        String noticeKey = "register-notice:" + user.getEmail();

        try {
            attemptLimiter.check(noticeKey, 1);
        } catch (TooManyAttemptsException e) {
            return;
        }
        attemptLimiter.record(noticeKey, rateLimit.registerNoticeWindow());

        String text = """
                Olá, %s!

                Alguém tentou criar uma conta no Money Pilot com este e-mail. Como você já tem uma conta, nada foi alterado.

                Se foi você, é só entrar com a sua senha. Se não foi, pode ignorar esta mensagem.
                """.formatted(user.getName());

        sendQuietly(new EmailMessage(user.getEmail(), "Tentativa de cadastro com o seu e-mail", text));
    }

    @Transactional
    public void confirm(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(opaqueTokens.hash(rawToken))
                .orElseThrow(InvalidVerificationTokenException::new);

        // Link já usado: a conta está verificada, então clicar de novo não é erro.
        if (token.getUsedAt() != null) {
            return;
        }

        Instant now = clock.instant();

        if (!token.getExpiresAt().isAfter(now)) {
            throw new InvalidVerificationTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidVerificationTokenException::new);

        user.setEmailVerifiedAt(now);
        token.setUsedAt(now);
        userRepository.saveAndFlush(user);
        tokenRepository.saveAndFlush(token);
    }

    /** Responde igual exista ou não a conta; falhas de envio só vão para o log. */
    public void resend(String email, String clientIp) {
        String emailKey = "verification-resend:" + email;
        String ipKey = "verification-resend-ip:" + clientIp;

        attemptLimiter.check(ipKey, rateLimit.verificationResendPerIp());
        attemptLimiter.check(emailKey, rateLimit.verificationResendPerEmail());
        attemptLimiter.record(ipKey, rateLimit.verificationResendWindow());
        attemptLimiter.record(emailKey, rateLimit.verificationResendWindow());

        User user = userRepository.findUserByEmail(email).orElse(null);

        if (user == null) {
            return;
        }

        if (user.getEmailVerifiedAt() != null) {
            sendQuietly(alreadyActiveMessage(user));
            return;
        }

        try {
            sendLink(user);
        } catch (RuntimeException e) {
            log.error("Could not send the verification link: {}", e.getClass().getSimpleName());
        }
    }

    private void sendQuietly(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (RuntimeException e) {
            log.error("Could not send the account notice: {}", e.getClass().getSimpleName());
        }
    }

    private EmailMessage verificationMessage(User user, String rawToken) {
        String link = appProperties.publicUrl() + "/app/#/verificar-email?token=" + rawToken;
        String text = """
                Olá, %s!

                Para ativar a sua conta no Money Pilot, abra o link abaixo (vale por 24 horas e só pode ser usado uma vez):

                %s

                Se você não criou uma conta, ignore esta mensagem.
                """.formatted(user.getName(), link);

        return new EmailMessage(user.getEmail(), "Ative a sua conta no Money Pilot", text);
    }

    private EmailMessage alreadyActiveMessage(User user) {
        String text = """
                Olá, %s!

                Recebemos um pedido de link de ativação, mas a sua conta no Money Pilot já está ativa. É só entrar com o seu e-mail e senha.

                Se você não fez esse pedido, pode ignorar esta mensagem.
                """.formatted(user.getName());

        return new EmailMessage(user.getEmail(), "A sua conta já está ativa", text);
    }
}
