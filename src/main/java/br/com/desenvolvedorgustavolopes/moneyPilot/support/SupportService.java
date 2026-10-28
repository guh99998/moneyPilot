package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AttemptLimiter;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.RateLimitProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.User;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.UserRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.EmailSendException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.SupportEmailRequiredException;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private static final int REFERENCE_ATTEMPTS = 5;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final AuthenticatedUserProvider authenticatedUser;
    private final UserRepository userRepository;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final EmailSender emailSender;
    private final SupportProperties properties;
    private final SupportReference references;
    private final SupportTicketRepository tickets;
    private final Clock clock;

    /**
     * Sem transação de propósito: o chamado é gravado antes do envio e fica gravado mesmo que o provedor de e-mail
     * falhe (a pessoa vê "recebido" e o painel marca "e-mail não enviado"). Como o chamado foi recebido, ele conta
     * no limite sempre que é gravado.
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

        SupportTicket ticket = tickets.save(newTicket(request, replyTo, account));

        try {
            emailSender.send(message(request, ticket.getReference(), replyTo, account));
            ticket.setEmailSent(true);
            tickets.save(ticket);
        } catch (EmailSendException ex) {
            log.error("Support e-mail for ticket {} was not sent; the ticket stays saved", ticket.getReference(), ex);
        }

        attemptLimiter.record(senderKey, rateLimit.supportWindow());
        attemptLimiter.record(ipKey, rateLimit.supportWindow());

        return new SupportTicketResponse(ticket.getReference(), replyTo);
    }

    private SupportTicket newTicket(SupportTicketRequest request, String replyTo, User account) {
        SupportTicket ticket = new SupportTicket();
        ticket.setReference(uniqueReference());
        ticket.setCategory(request.category());
        ticket.setMessage(request.message());
        ticket.setReplyTo(replyTo);
        ticket.setUserId(account == null ? null : account.getId());
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setEmailSent(false);
        ticket.setCreatedAt(Instant.now(clock));
        return ticket;
    }

    private String uniqueReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            String reference = references.next();
            if (!tickets.existsByReference(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not generate a unique support reference");
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
