package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, Long> {

    Optional<EmailChangeToken> findByTokenHash(String tokenHash);

    Optional<EmailChangeToken> findByUserId(Long userId);
}
