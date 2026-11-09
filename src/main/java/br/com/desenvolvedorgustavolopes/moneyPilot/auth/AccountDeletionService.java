package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.RecurringPurchaseRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRecurrenceRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.budget.BudgetRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.DeletionConfirmationException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.GoogleAccountMismatchException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidCurrentPasswordException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleCredentialException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidGoogleTokenException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.UnauthenticatedRequestException;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailSender;
import br.com.desenvolvedorgustavolopes.moneyPilot.notification.DismissedAlertRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.onboarding.OnboardingRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.support.SupportTicketRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Exclusão da própria conta (spec 012): confirma quem é, apaga tudo numa transação e só então avisa por e-mail. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final AuthenticatedUserProvider userProvider;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleVerifier;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final TransactionTemplate transactionTemplate;
    private final EmailSender emailSender;

    private final BillRepository billRepository;
    private final BillRecurrenceRepository recurrenceRepository;
    private final RecurringPurchaseRepository recurringPurchaseRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final DismissedAlertRepository dismissedAlertRepository;
    private final OnboardingRepository onboardingRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailChangeTokenRepository emailChangeTokenRepository;

    public void delete(DeleteAccountRequest request) {
        User user = userRepository.findById(userProvider.getCurrentUserId())
                .orElseThrow(UnauthenticatedRequestException::new);

        confirm(user, request);

        String email = user.getEmail();
        String name = user.getName();
        Long userId = user.getId();

        transactionTemplate.executeWithoutResult(status -> deleteEverything(userId));
        attemptLimiter.reset(key(userId));

        sendNotice(email, name);
    }

    private void confirm(User user, DeleteAccountRequest request) {
        if (user.hasPassword()) {
            if (request.credential() != null) {
                throw new DeletionConfirmationException("credential", "Confirm with your password, not with Google");
            }
            confirmPassword(user, request.currentPassword());
        } else {
            if (request.currentPassword() != null) {
                throw new DeletionConfirmationException("currentPassword", "This account has no password: confirm with Google");
            }
            confirmGoogle(user, request.credential());
        }
    }

    private void confirmPassword(User user, String currentPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new DeletionConfirmationException("currentPassword", "Current password is required");
        }

        String key = key(user.getId());
        attemptLimiter.check(key, rateLimit.passwordChangeFailures());

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            attemptLimiter.record(key, rateLimit.passwordChangeWindow());
            throw new InvalidCurrentPasswordException();
        }
    }

    private void confirmGoogle(User user, String credential) {
        if (credential == null || credential.isBlank()) {
            throw new DeletionConfirmationException("credential", "Google credential is required");
        }

        GoogleIdentity identity;
        try {
            identity = googleVerifier.verify(credential);
        } catch (InvalidGoogleTokenException e) {
            throw new InvalidGoogleCredentialException();
        }

        if (user.getGoogleSubject() == null || !user.getGoogleSubject().equals(identity.subject())) {
            throw new GoogleAccountMismatchException();
        }
    }

    // Nenhuma FK tem ON DELETE CASCADE: filho antes de pai.
    private void deleteEverything(Long userId) {
        billRepository.deleteAllByUserId(userId);
        recurrenceRepository.deleteAllByUserId(userId);
        recurringPurchaseRepository.deleteAllByUserId(userId);
        transactionRepository.deleteAllByUserId(userId);
        budgetRepository.deleteAllByUserId(userId);
        dismissedAlertRepository.deleteAllByUserId(userId);
        onboardingRepository.deleteAllByUserId(userId);
        supportTicketRepository.deleteAllByUserId(userId);
        categoryRepository.deleteAllByUserId(userId);
        accountRepository.deleteAllByUserId(userId);
        refreshTokenRepository.deleteAllByUserId(userId);
        verificationTokenRepository.deleteAllByUserId(userId);
        passwordResetTokenRepository.deleteAllByUserId(userId);
        emailChangeTokenRepository.deleteAllByUserId(userId);
        userRepository.deleteUserById(userId);
    }

    /** O aviso é só informativo: falha no envio vai para o log e não desfaz a exclusão. */
    private void sendNotice(String email, String name) {
        String text = """
                Olá, %s!

                A sua conta no Money Pilot foi excluída, junto com todos os seus dados, e as sessões abertas foram encerradas.

                Se não foi você, entre em contato pelo formulário de suporte do site.
                """.formatted(name);
        try {
            emailSender.send(new EmailMessage(email, "Sua conta foi excluída", text));
        } catch (RuntimeException e) {
            log.error("Could not send the account deletion e-mail: {}", e.getClass().getSimpleName());
        }
    }

    private String key(Long userId) {
        return "password-change:" + userId;
    }
}
