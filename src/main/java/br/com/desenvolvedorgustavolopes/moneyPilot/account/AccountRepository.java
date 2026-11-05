package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByIdAndUserId(Long accountId, Long userId);

    Page<Account> findAllByUserId(Long userId, Pageable pageable);

    /** Boas-vindas (spec 013): a pessoa já criou alguma conta bancária? */
    boolean existsByUserId(Long userId);

    /** Exclusão de conta (spec 012): apaga em lote, sem carregar entidades. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Account a WHERE a.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
