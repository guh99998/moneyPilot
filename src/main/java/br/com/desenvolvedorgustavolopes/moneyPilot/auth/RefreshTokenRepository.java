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
}
