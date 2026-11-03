package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, Long> {

    Optional<EmailChangeToken> findByTokenHash(String tokenHash);

    Optional<EmailChangeToken> findByUserId(Long userId);

    /** Exclusão de conta (spec 012): apaga em lote, sem carregar entidades. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM EmailChangeToken t WHERE t.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
