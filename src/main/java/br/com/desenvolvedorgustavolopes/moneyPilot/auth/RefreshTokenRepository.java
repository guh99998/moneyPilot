package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String token);

    @Modifying(clearAutomatically = true)
    @Query("""
    UPDATE RefreshToken rt
    SET rt.revokedAt = :now 
    WHERE rt.userId = :userId AND rt.revokedAt IS NULL
    """)
    int revokeAllActiveByUserId(@Param("userId") Long userId,
                                @Param("now")Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
    UPDATE RefreshToken rt
    SET rt.revokedAt = :now
    WHERE rt.id = :id AND rt.revokedAt IS NULL
    """)
    int revokeIfActive(@Param("id") Long id,
                       @Param("now") Instant now);

    /**
     * Apaga (em vez de revogar) as sessões ativas: um token revogado que volta a ser usado é tratado
     * como roubo e derruba todas as sessões, inclusive a que deve continuar aberta.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
    DELETE FROM RefreshToken rt
    WHERE rt.userId = :userId AND rt.id <> :keepId AND rt.revokedAt IS NULL
    """)
    int deleteAllActiveByUserIdExcept(@Param("userId") Long userId,
                                      @Param("keepId") Long keepId);
}
