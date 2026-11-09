package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringPurchaseRepository extends JpaRepository<RecurringPurchase, Long> {

    Optional<RecurringPurchase> findByIdAndUserId(Long id, Long userId);

    Page<RecurringPurchase> findAllByUserId(Long userId, Pageable pageable);

    Page<RecurringPurchase> findAllByUserIdAndAccountId(Long userId, Long accountId, Pageable pageable);

    List<RecurringPurchase> findAllByUserIdAndActiveTrue(Long userId);

    List<RecurringPurchase> findAllByAccountIdAndActiveTrue(Long accountId);

    /**
     * Avança a marca d'água só se ninguém a mexeu desde a leitura: duas gerações simultâneas não
     * criam a mesma cobrança duas vezes. Devolve 1 para quem ganhou e 0 para quem perdeu.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RecurringPurchase r SET r.generatedUntil = :next WHERE r.id = :id AND r.generatedUntil IS NULL")
    int advanceFirstGeneration(@Param("id") Long id, @Param("next") LocalDate next);

    /** Como {@link #advanceFirstGeneration}, para quando já existe uma cobrança gerada. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RecurringPurchase r SET r.generatedUntil = :next WHERE r.id = :id AND r.generatedUntil = :previous")
    int advanceGeneratedUntil(@Param("id") Long id, @Param("previous") LocalDate previous, @Param("next") LocalDate next);

    /** Exclusão de conta (spec 012): apaga em lote, sem carregar entidades. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RecurringPurchase r WHERE r.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
