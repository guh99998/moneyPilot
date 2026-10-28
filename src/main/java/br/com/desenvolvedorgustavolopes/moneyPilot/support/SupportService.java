package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AttemptLimiter;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RateLimitProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.User;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.UserRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.SupportEmailRequiredException;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SupportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final AuthenticatedUserProvider authenticatedUser;
    private final UserRepository userRepository;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final EmailSender emailSender;
    private final SupportProperties properties;
    private final SupportReference references;
    private final Clock clock;

    /**
     * Sem transação: o envio é a única coisa que importa e só conta no limite depois de feito, para que uma falha
     * do provedor não gaste a cota de quem tenta de novo.
     */
    public SupportTicketResponse send(SupportTicketRequest request, String clientIp) {
        User account = authenticatedUser.findCurrentUserId().flatMap(userRepository::findById).orElse(null);

        String replyTo;
        String senderKey;
        if (account != null) {
            replyTo = account.getEmail();
            senderKey = "support-account:" + account.getId();
        } else {
            if (request.email() == null) {
                throw new SupportEmailRequiredException();
            }
            replyTo = request.email();
            senderKey = "support-email:" + replyTo;
        }
        String ipKey = "support-ip:" + clientIp;

        attemptLimiter.check(senderKey, rateLimit.supportPerSender());
        attemptLimiter.check(ipKey, rateLimit.supportPerIp());

        String reference = references.next();
        emailSender.send(message(request, reference, replyTo, account));

        attemptLimiter.record(senderKey, rateLimit.supportWindow());
        attemptLimiter.record(ipKey, rateLimit.supportWindow());

        return new SupportTicketResponse(reference, replyTo);
    }

    private EmailMessage message(SupportTicketRequest request, String reference, String replyTo, User account) {
        String label = request.category().label();
        String sentAt = ZonedDateTime.now(clock).format(DATE_TIME);
        String accountLine = Optional.ofNullable(account)
                .map(user -> "Nome da conta: " + user.getName() + "\n")
                .orElse("");

        String text = """
                Chamado %s

                Categoria: %s
                E-mail: %s
                Logada: %s
                %sData: %s

                Mensagem:
                %s
                """.formatted(reference, label, replyTo, account == null ? "não" : "sim", accountLine, sentAt,
                request.message());

        return new EmailMessage(properties.email(), "[Suporte " + reference + "] " + label, text, null, replyTo);
    }
}
