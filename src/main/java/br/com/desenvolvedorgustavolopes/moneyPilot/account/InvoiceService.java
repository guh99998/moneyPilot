package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.AccountNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.AccountWithoutInvoicesException;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.AccountDayTotals;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fatura do cartão (spec 016), calculada em leitura: nada é gravado. Agrupa por ciclo o que o cartão
 * tem desde {@code invoicesFrom} e abate os pagamentos (transferências para o cartão) da fatura mais
 * antiga para a mais nova.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuthenticatedUserProvider userProvider;
    private final Clock clock;

    public List<InvoiceResponse> getInvoices(Long accountId) {
        Long userId = userProvider.getCurrentUserId();
        Account account = accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (account.getClosingDay() == null) {
            throw new AccountWithoutInvoicesException(accountId);
        }

        return this.invoicesOf(account, LocalDate.now(clock));
    }

    /** Faturas de um cartão com os dias informados, da mais antiga à mais nova. */
    public List<InvoiceResponse> invoicesOf(Account account, LocalDate today) {
        InvoiceCycle cycles = new InvoiceCycle(account.getClosingDay(), account.getDueDay());

        Map<LocalDate, BigDecimal> totalByCycle = new LinkedHashMap<>();
        BigDecimal payments = BigDecimal.ZERO;
        InvoiceCycle.Cycle last = cycles.cycleOf(today);

        for (AccountDayTotals day : transactionRepository.findDayTotalsSince(account.getId(), account.getInvoicesFrom())) {
            InvoiceCycle.Cycle cycle = cycles.cycleOf(day.getDate());
            totalByCycle.merge(cycle.start(), day.getExpense().subtract(day.getIncome()), BigDecimal::add);
            payments = payments.add(day.getTransferIn());
            if (cycle.start().isAfter(last.start())) {
                last = cycle;
            }
        }

        List<InvoiceResponse> invoices = new ArrayList<>();
        BigDecimal pool = payments;
        InvoiceCycle.Cycle cycle = cycles.cycleOf(account.getInvoicesFrom());

        while (!cycle.start().isAfter(last.start())) {
            BigDecimal total = totalByCycle.getOrDefault(cycle.start(), BigDecimal.ZERO);
            boolean current = !today.isBefore(cycle.start()) && !today.isAfter(cycle.end());

            BigDecimal paid = BigDecimal.ZERO;
            if (total.signum() > 0) {
                paid = pool.min(total);
                pool = pool.subtract(paid);
            } else {
                // Estorno maior que as compras do ciclo vira crédito para as faturas seguintes.
                pool = pool.subtract(total);
            }
            BigDecimal remaining = total.signum() > 0 ? total.subtract(paid) : BigDecimal.ZERO;

            if (total.signum() != 0 || current) {
                invoices.add(new InvoiceResponse(
                        cycle.start(), cycle.end(), cycle.closingDate(), cycle.dueDate(),
                        total, paid, remaining, this.statusOf(cycle, remaining, today)));
            }
            cycle = cycles.next(cycle);
        }

        return invoices;
    }

    /**
     * Spec 016, regra 10: faturas a pagar dos cartões fora do disponível, até a data limite. Cartão
     * marcado no disponível não projeta, porque a compra já reduziu o saldo.
     */
    public List<InvoiceDue> payableInvoices(Long userId, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        List<InvoiceDue> due = new ArrayList<>();

        for (Account card : accountRepository.findAllByUserIdAndClosingDayIsNotNullAndCountsAsAvailableFalse(userId)) {
            for (InvoiceResponse invoice : this.invoicesOf(card, today)) {
                if (invoice.remaining().signum() > 0 && !invoice.dueDate().isAfter(to)) {
                    due.add(new InvoiceDue(invoice.dueDate(), invoice.remaining()));
                }
            }
        }

        return due;
    }

    /** Uma fatura com valor a pagar e a data em que vence. */
    public record InvoiceDue(LocalDate dueDate, BigDecimal amount) {
    }

    private InvoiceStatus statusOf(InvoiceCycle.Cycle cycle, BigDecimal remaining, LocalDate today) {
        if (today.isBefore(cycle.start())) {
            return InvoiceStatus.FUTURE;
        }
        if (!today.isAfter(cycle.end())) {
            return InvoiceStatus.OPEN;
        }
        if (remaining.signum() == 0) {
            return InvoiceStatus.PAID;
        }
        return cycle.dueDate().isBefore(today) ? InvoiceStatus.OVERDUE : InvoiceStatus.CLOSED;
    }
}
