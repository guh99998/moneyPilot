package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByIdAndUserId(Long accountId, Long userId);

    Page<Account> findAllByUserId(Long userId, Pageable pageable);
}
