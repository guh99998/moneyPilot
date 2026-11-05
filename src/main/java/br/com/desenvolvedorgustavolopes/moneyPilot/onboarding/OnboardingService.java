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

/** Cartão de primeiros passos do painel (spec 013). */
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
     * O cartão da conta autenticada. Os passos são conferidos ao ler e o que está feito é gravado: passo feito
     * nunca desmarca, mesmo que a pessoa apague depois o que o cumpriu. Conta antiga (sem linha), conta que
     * dispensou o cartão e conta que concluiu os três passos recebem {@code visible: false}.
     */
    @Transactional
    public OnboardingResponse get() {
        Long userId = userProvider.getCurrentUserId();
        Onboarding onboarding = onboardingRepository.findById(userId).orElse(null);

        if (onboarding == null || onboarding.getDismissedAt() != null) {
            return OnboardingResponse.hidden();
        }

        Instant now = clock.instant();

        if (onboarding.getAccountDoneAt() == null && accountRepository.existsByUserId(userId)) {
            onboarding.setAccountDoneAt(now);
        }
        if (onboarding.getTransactionDoneAt() == null && transactionRepository.existsAnyByUserId(userId)) {
            onboarding.setTransactionDoneAt(now);
        }
        if (onboarding.getBudgetDoneAt() == null && budgetRepository.existsByUserId(userId)) {
            onboarding.setBudgetDoneAt(now);
        }
        onboardingRepository.save(onboarding);

        if (onboarding.allStepsDone()) {
            return OnboardingResponse.hidden();
        }

        return new OnboardingResponse(true, List.of(
                new OnboardingStepResponse(OnboardingStep.ACCOUNT, onboarding.getAccountDoneAt() != null),
                new OnboardingStepResponse(OnboardingStep.TRANSACTION, onboarding.getTransactionDoneAt() != null),
                new OnboardingStepResponse(OnboardingStep.BUDGET, onboarding.getBudgetDoneAt() != null)
        ));
    }

    /** Esconde o cartão em qualquer aparelho. Idempotente; conta antiga (sem linha) não muda nada. */
    @Transactional
    public void dismiss() {
        onboardingRepository.findById(userProvider.getCurrentUserId()).ifPresent(onboarding -> {
            if (onboarding.getDismissedAt() == null) {
                onboarding.setDismissedAt(clock.instant());
                onboardingRepository.save(onboarding);
            }
        });
    }
}
