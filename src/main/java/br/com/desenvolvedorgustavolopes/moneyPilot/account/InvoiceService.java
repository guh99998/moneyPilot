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
import java.util.Comparator;
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
    private final RecurringPurchaseRepository recurringPurchaseRepository;
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

        LocalDate today = LocalDate.now(clock);
        InvoiceCycle cycles = new InvoiceCycle(account.getClosingDay(), account.getDueDay());

        // Regra 35: a tela Faturas projeta as compras recorrentes até o fim do ciclo seguinte ao atual.
        return this.invoicesOf(account, today, cycles.next(cycles.cycleOf(today)).end());
    }

    /** Spec 016, regra 17: faturas aberta, fechada e vencida com valor a pagar, de todos os cartões, por vencimento. */
    public List<PayableInvoiceResponse> listPayable() {
        return this.payableOf(userProvider.getCurrentUserId(), LocalDate.now(clock));
    }

    public List<PayableInvoiceResponse> payableOf(Long userId, LocalDate today) {
        List<PayableInvoiceResponse> payable = new ArrayList<>();

        for (Account card : accountRepository.findAllByUserIdAndClosingDayIsNotNull(userId)) {
            for (InvoiceResponse invoice : this.invoicesOf(card, today)) {
                if (invoice.status() == InvoiceStatus.FUTURE || invoice.remaining().signum() <= 0) {
                    continue;
                }
                payable.add(new PayableInvoiceResponse(
                        card.getId(), card.getName(),
                        invoice.periodStart(), invoice.periodEnd(), invoice.closingDate(), invoice.dueDate(),
                        invoice.total(), invoice.paid(), invoice.remaining(), invoice.status()));
            }
        }

        payable.sort(Comparator.comparing(PayableInvoiceResponse::dueDate).thenComparing(PayableInvoiceResponse::accountId));
        return payable;
    }

    /** Faturas de um cartão com os dias informados, da mais antiga à mais nova, sem projeção. */
    public List<InvoiceResponse> invoicesOf(Account account, LocalDate today) {
        return this.invoicesOf(account, today, null);
    }

    /**
     * Como {@link #invoicesOf(Account, LocalDate)}, somando as cobranças de compras recorrentes ainda
     * não geradas até {@code horizon} (inclusive). Horizonte nulo não projeta nada.
     */
    public List<InvoiceResponse> invoicesOf(Account account, LocalDate today, LocalDate horizon) {
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

        // Regra 34: só entra o que ainda não foi gerado (depois de generatedUntil), então nunca conta duas vezes.
        Map<LocalDate, BigDecimal> projectedByCycle = new LinkedHashMap<>();
        if (horizon != null) {
            for (RecurringPurchase purchase : recurringPurchaseRepository.findAllByAccountIdAndActiveTrue(account.getId())) {
                for (LocalDate date : RecurringPurchaseSchedule.pendingDates(purchase, horizon)) {
                    if (date.isBefore(account.getInvoicesFrom())) {
                        continue;
                    }
                    InvoiceCycle.Cycle cycle = cycles.cycleOf(date);
                    totalByCycle.merge(cycle.start(), purchase.getAmount(), BigDecimal::add);
                    projectedByCycle.merge(cycle.start(), purchase.getAmount(), BigDecimal::add);
                    if (cycle.start().isAfter(last.start())) {
                        last = cycle;
                    }
                }
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
                        total, paid, remaining, this.statusOf(cycle, remaining, today),
                        projectedByCycle.getOrDefault(cycle.start(), BigDecimal.ZERO)));
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
            for (InvoiceResponse invoice : this.invoicesOf(card, today, to)) {
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
