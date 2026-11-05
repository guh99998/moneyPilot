package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Boas-vindas de uma conta nova (spec 013). A existência da linha também registra que o e-mail já foi enviado. */
@Entity
@Table(name = "onboarding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Onboarding {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "welcomed_at", nullable = false)
    private Instant welcomedAt;

    @Column(name = "account_done_at")
    private Instant accountDoneAt;

    @Column(name = "transaction_done_at")
    private Instant transactionDoneAt;

    @Column(name = "budget_done_at")
    private Instant budgetDoneAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    public boolean allStepsDone() {
        return accountDoneAt != null && transactionDoneAt != null && budgetDoneAt != null;
    }
}
