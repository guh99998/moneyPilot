package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    boolean existsByReference(String reference);

    Page<SupportTicket> findAllByStatus(SupportTicketStatus status, Pageable pageable);

    Page<SupportTicket> findAllByUserId(Long userId, Pageable pageable);

    Optional<SupportTicket> findByReferenceAndUserId(String reference, Long userId);

    List<SupportTicket> findAllByUserIdAndStatusChangedAtGreaterThanEqual(Long userId, Instant since);

    /** Exclusão de conta (spec 012): apaga em lote, sem carregar entidades. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SupportTicket t WHERE t.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
