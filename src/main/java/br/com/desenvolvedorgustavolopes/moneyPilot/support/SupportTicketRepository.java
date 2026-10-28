package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    boolean existsByReference(String reference);

    Page<SupportTicket> findAllByStatus(SupportTicketStatus status, Pageable pageable);
}
