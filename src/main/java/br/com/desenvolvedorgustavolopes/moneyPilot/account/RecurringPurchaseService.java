package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.Category;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryService;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.AccountNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.CategoryTypeMismatchException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidInvoiceDaysException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidRecurrenceDateRangeException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.RecurringPurchaseNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.Transaction;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionResponse;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Compra recorrente no cartão (spec 016, regras 31 a 38). */
@Service
@RequiredArgsConstructor
public class RecurringPurchaseService {

    private final RecurringPurchaseRepository repository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final AuthenticatedUserProvider userProvider;
    private final Clock clock;

    public RecurringPurchaseResponse create(RecurringPurchaseRequest request) {
        Long userId = userProvider.getCurrentUserId();
        Account card = this.validate(request, userId);
        Instant now = Instant.now();

        RecurringPurchase purchase = new RecurringPurchase();
        purchase.setUserId(userId);
        purchase.setActive(true);
        purchase.setCreatedAt(now);
        this.apply(purchase, request, now);

        return this.toResponse(repository.save(purchase), card);
    }

    public RecurringPurchaseResponse getById(Long id) {
        return this.toResponse(this.findOwned(id));
    }

    public Page<RecurringPurchaseResponse> getAll(Long accountId, Pageable pageable) {
        Long userId = userProvider.getCurrentUserId();

        if (accountId == null) {
            return repository.findAllByUserId(userId, pageable).map(this::toResponse);
        }

        accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(() -> new AccountNotFoundException(accountId));
        return repository.findAllByUserIdAndAccountId(userId, accountId, pageable).map(this::toResponse);
    }

    /** Regra 36: vale para as cobranças ainda não geradas; as já geradas ficam como estão. */
    public RecurringPurchaseResponse update(Long id, RecurringPurchaseRequest request) {
        RecurringPurchase purchase = this.findOwned(id);
        Account card = this.validate(request, purchase.getUserId());

        this.apply(purchase, request, Instant.now());

        return this.toResponse(repository.save(purchase), card);
    }

    /** Apaga a regra e mantém as cobranças já geradas (a ligação delas com a regra é desfeita no banco). */
    public void delete(Long id) {
        repository.delete(this.findOwned(id));
    }

    public RecurringPurchaseResponse deactivate(Long id) {
        RecurringPurchase purchase = this.findOwned(id);
        purchase.setActive(false);
        purchase.setUpdatedAt(Instant.now());

        return this.toResponse(repository.save(purchase));
    }

    /**
     * Regras 32 e 37: transforma em despesa no cartão cada cobrança já vencida e ainda não gerada.
     * Idempotente: a marca d'água só avança uma vez, mesmo com pedidos simultâneos.
     */
    @Transactional
    public List<TransactionResponse> materialize() {
        Long userId = userProvider.getCurrentUserId();
        LocalDate today = LocalDate.now(clock);
        List<TransactionResponse> created = new ArrayList<>();

        for (RecurringPurchase purchase : repository.findAllByUserIdAndActiveTrue(userId)) {
            List<LocalDate> dates = RecurringPurchaseSchedule.pendingDates(purchase, today);
            if (dates.isEmpty()) {
                continue;
            }

            LocalDate last = dates.get(dates.size() - 1);
            int won = purchase.getGeneratedUntil() == null
                    ? repository.advanceFirstGeneration(purchase.getId(), last)
                    : repository.advanceGeneratedUntil(purchase.getId(), purchase.getGeneratedUntil(), last);
            if (won == 0) {
                continue; // outro pedido gerou primeiro
            }

            Account card = accountRepository.findByIdAndUserId(purchase.getAccountId(), userId)
                    .orElseThrow(() -> new AccountNotFoundException(purchase.getAccountId()));
            Category category = categoryService.findVisibleCategory(purchase.getCategoryId());
            Instant now = Instant.now();

            List<Transaction> charges = new ArrayList<>();
            for (LocalDate date : dates) {
                Transaction charge = new Transaction();
                charge.setAccountId(purchase.getAccountId());
                charge.setCategoryId(purchase.getCategoryId());
                charge.setAmount(purchase.getAmount());
                charge.setType(TransactionType.EXPENSE);
                charge.setDescription(purchase.getDescription());
                charge.setDate(date);
                charge.setRecurringPurchaseId(purchase.getId());
                charge.setCreatedAt(now);
                charge.setUpdatedAt(now);
                charges.add(charge);
            }

            for (Transaction charge : transactionRepository.saveAll(charges)) {
                created.add(new TransactionResponse(charge, card.getName(), category.getName()));
            }
        }

        return created;
    }

    private Account validate(RecurringPurchaseRequest request, Long userId) {
        Account card = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        if (card.getType() != AccountType.CREDIT_CARD || card.getClosingDay() == null) {
            throw new InvalidInvoiceDaysException("accountId",
                    "A compra recorrente precisa de um cartão de crédito com fechamento e vencimento");
        }

        Category category = categoryService.findVisibleCategory(request.categoryId());
        if (!category.getType().name().equals(TransactionType.EXPENSE.name())) {
            throw new CategoryTypeMismatchException(request.categoryId(), TransactionType.EXPENSE);
        }

        if (request.endDate() != null && !request.endDate().isAfter(request.startDate())) {
            throw new InvalidRecurrenceDateRangeException(request.endDate(), request.startDate());
        }

        return card;
    }

    private void apply(RecurringPurchase purchase, RecurringPurchaseRequest request, Instant now) {
        purchase.setAccountId(request.accountId());
        purchase.setCategoryId(request.categoryId());
        purchase.setDescription(request.description());
        purchase.setAmount(request.amount());
        purchase.setDayOfMonth(request.dayOfMonth());
        purchase.setStartDate(request.startDate());
        purchase.setEndDate(request.endDate());
        purchase.setUpdatedAt(now);
    }

    private RecurringPurchase findOwned(Long id) {
        return repository.findByIdAndUserId(id, userProvider.getCurrentUserId())
                .orElseThrow(() -> new RecurringPurchaseNotFoundException(id));
    }

    private RecurringPurchaseResponse toResponse(RecurringPurchase purchase) {
        Account card = accountRepository.findByIdAndUserId(purchase.getAccountId(), purchase.getUserId())
                .orElseThrow(() -> new AccountNotFoundException(purchase.getAccountId()));
        return this.toResponse(purchase, card);
    }

    private RecurringPurchaseResponse toResponse(RecurringPurchase purchase, Account card) {
        Category category = categoryService.findVisibleCategory(purchase.getCategoryId());
        return new RecurringPurchaseResponse(purchase, card.getName(), category.getName());
    }
}
