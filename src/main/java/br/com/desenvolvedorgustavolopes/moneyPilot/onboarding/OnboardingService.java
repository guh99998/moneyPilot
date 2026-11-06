package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.budget.BudgetRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Primeiros passos: progresso (spec 013) e pop-up de primeiro contato (spec 027). */
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final OnboardingRepository onboardingRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final AuthenticatedUserProvider userProvider;
    private final Clock clock;

    /**
     * Estado do pop-up da conta autenticada. Os passos são sempre conferidos; só são gravados quando existe linha
     * (conta antiga não ganha linha) e passo feito nunca desmarca, mesmo que a pessoa apague depois o que o cumpriu.
     * {@code eligible}: tem linha, não dispensou o cartão antigo e ainda há passo pendente. {@code visible}: elegível
     * e com "Mostrar ao iniciar" marcado.
     */
    @Transactional
    public OnboardingResponse get() {
        Long userId = userProvider.getCurrentUserId();
        Onboarding onboarding = onboardingRepository.findById(userId).orElse(null);
        Instant now = clock.instant();

        Instant account = stepDone(onboarding == null ? null : onboarding.getAccountDoneAt(), now,
                () -> accountRepository.existsByUserId(userId));
        Instant transaction = stepDone(onboarding == null ? null : onboarding.getTransactionDoneAt(), now,
                () -> transactionRepository.existsAnyByUserId(userId));
        Instant budget = stepDone(onboarding == null ? null : onboarding.getBudgetDoneAt(), now,
                () -> budgetRepository.existsByUserId(userId));

        boolean allDone = account != null && transaction != null && budget != null;
        boolean eligible = false;
        boolean showOnStart = true;

        if (onboarding != null) {
            onboarding.setAccountDoneAt(account);
            onboarding.setTransactionDoneAt(transaction);
            onboarding.setBudgetDoneAt(budget);
            onboardingRepository.save(onboarding);
            eligible = onboarding.getDismissedAt() == null && !allDone;
            showOnStart = onboarding.isShowOnStart();
        }

        return new OnboardingResponse(eligible && showOnStart, eligible, showOnStart, List.of(
                new OnboardingStepResponse(OnboardingStep.ACCOUNT, account != null),
                new OnboardingStepResponse(OnboardingStep.TRANSACTION, transaction != null),
                new OnboardingStepResponse(OnboardingStep.BUDGET, budget != null)
        ));
    }

    /** Guarda "Mostrar ao iniciar" para a conta. Idempotente; conta antiga (sem linha) não muda nada. */
    @Transactional
    public void savePreferences(OnboardingPreferencesRequest request) {
        onboardingRepository.findById(userProvider.getCurrentUserId()).ifPresent(onboarding -> {
            if (onboarding.isShowOnStart() != request.showOnStart()) {
                onboarding.setShowOnStart(request.showOnStart());
                onboardingRepository.save(onboarding);
            }
        });
    }

    /** Passo já gravado continua feito; senão confere agora. */
    private Instant stepDone(Instant recorded, Instant now, BooleanSupplier doneNow) {
        if (recorded != null) {
            return recorded;
        }
        return doneNow.getAsBoolean() ? now : null;
    }
}
