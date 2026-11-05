package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface OnboardingRepository extends JpaRepository<Onboarding, Long> {

    /**
     * Cria a linha só se a conta ainda não passou pelas boas-vindas. Devolve 1 quando criou e 0 quando já existia,
     * de forma atômica: dois cliques no link de verificação ao mesmo tempo não geram dois e-mails.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO onboarding (user_id, welcomed_at) VALUES (:userId, :welcomedAt) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("welcomedAt") Instant welcomedAt);

    /** Exclusão de conta (spec 012): apaga em lote, sem carregar entidades. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Onboarding o WHERE o.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
