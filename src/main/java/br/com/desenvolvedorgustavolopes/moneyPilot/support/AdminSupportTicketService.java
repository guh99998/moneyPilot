package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.UserRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.SupportTicketNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminSupportTicketService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AdminAccess adminAccess;
    private final SupportTicketRepository tickets;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<SupportTicketSummaryResponse> list(SupportTicketStatus status, Pageable pageable) {
        adminAccess.requireAdmin();

        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), NEWEST_FIRST);
        Page<SupportTicket> page = status == null ? tickets.findAll(newestFirst) : tickets.findAllByStatus(status, newestFirst);
        return page.map(SupportTicketSummaryResponse::new);
    }

    @Transactional(readOnly = true)
    public SupportTicketDetailResponse get(Long id) {
        adminAccess.requireAdmin();
        return detail(find(id));
    }

    @Transactional
    public SupportTicketDetailResponse updateStatus(Long id, UpdateSupportTicketStatusRequest request) {
        adminAccess.requireAdmin();

        SupportTicket ticket = find(id);
        if (ticket.getStatus() != request.status()) {
            ticket.setStatus(request.status());
            ticket.setStatusChangedAt(Instant.now(clock));
            tickets.save(ticket);
        }
        return detail(ticket);
    }

    private SupportTicket find(Long id) {
        return tickets.findById(id).orElseThrow(() -> new SupportTicketNotFoundException(id));
    }

    private SupportTicketDetailResponse detail(SupportTicket ticket) {
        String accountName = ticket.getUserId() == null ? null
                : userRepository.findById(ticket.getUserId()).map(user -> user.getName()).orElse(null);
        return new SupportTicketDetailResponse(ticket, accountName);
    }
}
