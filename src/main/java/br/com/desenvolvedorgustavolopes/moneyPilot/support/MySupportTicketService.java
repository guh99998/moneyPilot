package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.SupportTicketNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Os chamados abertos com a conta logada. Chamado de outra conta é tratado como inexistente. */
@Service
@RequiredArgsConstructor
public class MySupportTicketService {

    static final int PAGE_SIZE = 10;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AuthenticatedUserProvider userProvider;
    private final SupportTicketRepository tickets;

    @Transactional(readOnly = true)
    public Page<MySupportTicketSummaryResponse> list(int page) {
        Long userId = userProvider.getCurrentUserId();
        return tickets.findAllByUserId(userId, PageRequest.of(Math.max(page, 0), PAGE_SIZE, NEWEST_FIRST))
                .map(MySupportTicketSummaryResponse::new);
    }

    @Transactional(readOnly = true)
    public MySupportTicketDetailResponse get(String reference) {
        Long userId = userProvider.getCurrentUserId();
        return tickets.findByReferenceAndUserId(reference, userId)
                .map(MySupportTicketDetailResponse::new)
                .orElseThrow(() -> new SupportTicketNotFoundException(reference));
    }
}
