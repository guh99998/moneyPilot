package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.Account;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.Bill;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.Category;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryService;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;
    private final AuthenticatedUserProvider userProvider;
    private final AccountRepository accountRepository;
    private final CategoryService categoryService;
    private final BillRepository billRepository;

    public Page<TransactionResponse> getAllTransactions(Long accountId, Long categoryId, LocalDate from, LocalDate to, TransactionFilterType type, Pageable pageable) {
        Long userId = userProvider.getCurrentUserId();

        if (accountId != null) {
            if (accountRepository.findByIdAndUserId(accountId, userId).isEmpty()) {
                throw new AccountNotFoundException(accountId);
            }
        }

        TransactionType queryType;
        Boolean transferOnly;

        if (type == null) {
            queryType = null;
            transferOnly = null;
        } else if (type == TransactionFilterType.TRANSFER) {
            queryType = null;
            transferOnly = true;
        } else {
            transferOnly = null;
            queryType = TransactionType.valueOf(type.name());
        }

        Pageable pageableComDesempate = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Transaction> page = repository.findAllFiltered(userId, accountId, categoryId, from, to, queryType, transferOnly, pageableComDesempate);

        // Uma consulta por página para saber quais lançamentos vieram de baixa de título.
        Map<Long, Bill> billsByTransaction = billRepository
                .findAllByTransactionIdIn(page.getContent().stream().map(Transaction::getId).toList())
                .stream()
                .collect(Collectors.toMap(Bill::getTransactionId, Function.identity()));

        // Uma consulta por página para saber quais compras foram estornadas (spec 028).
        Map<Long, Long> refundsByPurchase = repository
                .findAllByRefundOfTransactionIdIn(page.getContent().stream().map(Transaction::getId).toList())
                .stream()
                .collect(Collectors.toMap(Transaction::getRefundOfTransactionId, Transaction::getId));

        return page.
                map(
                        t -> {Account account = accountRepository.findById(t.getAccountId()).orElseThrow(() -> new AccountNotFoundException(t.getAccountId()));
                            String categoryName;
                            if(t.getCategoryId() != null){
                                Category category = categoryService.findVisibleCategory(t.getCategoryId());
                                categoryName = category.getName();
                            } else {
                                categoryName = null;
                            }
                            return new TransactionResponse(t, account.getName(), categoryName, billsByTransaction.get(t.getId()), refundsByPurchase.get(t.getId()));
                        });
    }

    public TransactionResponse createTransaction(TransactionRequest request) {
        Long userId = userProvider.getCurrentUserId();
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId).orElseThrow(() -> new AccountNotFoundException(request.accountId()));
        Category category = categoryService.findVisibleCategory(request.categoryId());

        if (!category.getType().name().equals(request.type().name()))
            throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        Transaction transaction = new Transaction();

        transaction.setAccountId(request.accountId());
        transaction.setCategoryId(request.categoryId());
        transaction.setAmount(request.amount());
        transaction.setType(request.type());
        transaction.setDescription(request.description());
        transaction.setDate(request.date());
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());

        return new TransactionResponse(repository.save(transaction), account.getName(), category.getName());
    }

    private Transaction findOwnedTransaction(Long transactionId) {
        Long userId = userProvider.getCurrentUserId();
        Transaction transaction = repository.findById(transactionId).orElseThrow(() -> new TransactionNotFoundException(transactionId));

        accountRepository.findByIdAndUserId(transaction.getAccountId(), userId).orElseThrow(() -> new TransactionNotFoundException(transactionId));

        return transaction;
    }

    public TransactionResponse getTransactionById(Long id) {
        Transaction transaction = this.findOwnedTransaction(id);
        Account account = accountRepository.findById(transaction.getAccountId()).orElseThrow(() -> new AccountNotFoundException(transaction.getAccountId()));
        Category category = categoryService.findVisibleCategory(transaction.getCategoryId());
        Bill bill = billRepository.findAllByTransactionIdIn(List.of(transaction.getId())).stream().findFirst().orElse(null);

        Long refundId = repository.findAllByRefundOfTransactionIdIn(List.of(transaction.getId())).stream().findFirst().map(Transaction::getId).orElse(null);

        return new TransactionResponse(transaction, account.getName(), category.getName(), bill, refundId);
    }

    @Transactional
    public TransactionResponse updateTransaction(Long id, TransactionRequest request, EditScope scope) {
        Transaction transaction = this.findOwnedTransaction(id);
        Long userId = userProvider.getCurrentUserId();
        EditScope editScope = scope == null ? EditScope.ONLY_THIS : scope;
        boolean installment = transaction.getInstallmentGroupId() != null;

        if (transaction.getTransferGroupId() != null)
            throw new TransactionIsTransferException(id);

        if (transaction.getRefundOfTransactionId() != null)
            throw new TransactionIsRefundException(id);

        if (billRepository.existsByTransactionId(id))
            throw new TransactionLinkedToBillException(id);

        if (editScope == EditScope.THIS_AND_FOLLOWING && !installment)
            throw new InvalidInstallmentRequestException("scope", "O alcance 'esta e as seguintes' só vale para parcela");

        if (installment) {
            // Spec 028, regra 2: a parcela continua no cartão e no tipo em que nasceu.
            if (!transaction.getAccountId().equals(request.accountId()))
                throw new InvalidInstallmentRequestException("accountId", "A conta de uma parcela não muda");
            if (transaction.getType() != request.type())
                throw new InvalidInstallmentRequestException("type", "O tipo de uma parcela não muda");
        }

        List<Transaction> range = editScope == EditScope.THIS_AND_FOLLOWING
                ? repository.findAllByInstallmentGroupIdAndInstallmentNumberGreaterThanEqual(transaction.getInstallmentGroupId(), transaction.getInstallmentNumber())
                : List.of(transaction);

        if (repository.existsByRefundOfTransactionIdIn(range.stream().map(Transaction::getId).toList()))
            throw new TransactionIsRefundedException(id);

        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId).orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        Category category = categoryService.findVisibleCategory(request.categoryId());

        if (!category.getType().name().equals(request.type().name()))
            throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        Instant now = Instant.now();

        for (Transaction item : range) {
            item.setCategoryId(request.categoryId());
            item.setAmount(request.amount());
            item.setDescription(request.description());
            item.setUpdatedAt(now);
        }

        transaction.setAccountId(request.accountId());
        transaction.setType(request.type());
        transaction.setDate(request.date());

        repository.saveAll(range);

        return new TransactionResponse(transaction, account.getName(), category.getName());
    }

    public void deleteTransaction(Long id) {
        Transaction transaction = this.findOwnedTransaction(id);

        if (billRepository.existsByTransactionId(id))
            throw new TransactionLinkedToBillException(id);

        // Spec 028, regra 12: compra estornada não se apaga sem desfazer o estorno.
        if (repository.existsByRefundOfTransactionId(id))
            throw new TransactionIsRefundedException(id);

        if (transaction.getInstallmentGroupId() != null
                && repository.existsByRefundOfTransactionIdIn(repository.findAllByInstallmentGroupId(transaction.getInstallmentGroupId()).stream().map(Transaction::getId).toList()))
            throw new TransactionIsRefundedException(id);

        if (transaction.getTransferGroupId() != null) {
            repository.deleteAll(repository.findAllByTransferGroupId(transaction.getTransferGroupId()));
        } else if (transaction.getInstallmentGroupId() != null) {
            repository.deleteAll(repository.findAllByInstallmentGroupId(transaction.getInstallmentGroupId()));
        } else {
            repository.delete(transaction);
        }
    }

    @Transactional
    public List<TransactionResponse> createTransfer(TransferRequest request) {
        Long userId = userProvider.getCurrentUserId();
        if (request.fromAccountId().equals(request.toAccountId()))
            throw new TransferSameAccountException();

        Account fromAccount = accountRepository.findByIdAndUserId(request.fromAccountId(), userId).orElseThrow(() -> new AccountNotFoundException(request.fromAccountId()));
        Account toAccount = accountRepository.findByIdAndUserId(request.toAccountId(), userId).orElseThrow(() -> new AccountNotFoundException(request.toAccountId()));

        UUID transferGroupId = UUID.randomUUID();
        Transaction fromTransaction = new Transaction();

        fromTransaction.setAccountId(fromAccount.getId());
        fromTransaction.setCategoryId(null);
        fromTransaction.setAmount(request.amount());
        fromTransaction.setType(TransactionType.EXPENSE);
        fromTransaction.setDescription(request.description());
        fromTransaction.setDate(request.date());
        fromTransaction.setTransferGroupId(transferGroupId);
        fromTransaction.setCreatedAt(Instant.now());
        fromTransaction.setUpdatedAt(Instant.now());

        Transaction toTransaction = new Transaction();

        toTransaction.setAccountId(toAccount.getId());
        toTransaction.setCategoryId(null);
        toTransaction.setAmount(request.amount());
        toTransaction.setType(TransactionType.INCOME);
        toTransaction.setDescription(request.description());
        toTransaction.setDate(request.date());
        toTransaction.setTransferGroupId(transferGroupId);
        toTransaction.setCreatedAt(Instant.now());
        toTransaction.setUpdatedAt(Instant.now());

        Transaction fromTransactionSave = repository.save(fromTransaction);
        Transaction toTransactionSave = repository.save(toTransaction);

        return List.of(new TransactionResponse(fromTransactionSave, fromAccount.getName(), null), new TransactionResponse(toTransactionSave, toAccount.getName(), null));
    }

    /** Spec 016, regra 8: N despesas no cartão, uma por mês; o centavo que sobra fica na primeira parcela. */
    @Transactional
    public List<TransactionResponse> createInstallmentPurchase(InstallmentPurchaseRequest request) {
        Long userId = userProvider.getCurrentUserId();
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId).orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        if (account.getType() != AccountType.CREDIT_CARD)
            throw new InstallmentRequiresCreditCardException(account.getId());

        Category category = categoryService.findVisibleCategory(request.categoryId());

        if (!category.getType().name().equals(TransactionType.EXPENSE.name()))
            throw new CategoryTypeMismatchException(request.categoryId(), TransactionType.EXPENSE);

        int count = request.installments();
        boolean hasTotal = request.totalAmount() != null;
        boolean hasInstallmentAmount = request.installmentAmount() != null;

        // Spec 016, regras 39 a 41: um dos dois valores; parcela atual só com o valor da parcela.
        if (hasTotal && hasInstallmentAmount)
            throw new InvalidInstallmentRequestException("installmentAmount", "Informe o valor total ou o valor da parcela, não os dois");
        if (!hasTotal && !hasInstallmentAmount)
            throw new InvalidInstallmentRequestException("totalAmount", "Informe o valor total ou o valor da parcela");
        if (request.currentInstallment() != null && hasTotal)
            throw new InvalidInstallmentRequestException("currentInstallment", "A parcela atual só vale com o valor da parcela");

        int first = request.currentInstallment() == null ? 1 : request.currentInstallment();
        if (first > count)
            throw new InvalidInstallmentRequestException("currentInstallment", "A parcela atual não pode passar do número de parcelas");

        BigDecimal base = BigDecimal.ZERO;
        BigDecimal residue = BigDecimal.ZERO;
        if (hasTotal) {
            if (request.totalAmount().compareTo(BigDecimal.valueOf(count, 2)) < 0)
                throw new InstallmentAmountTooSmallException(BigDecimal.valueOf(count, 2), count);

            base = request.totalAmount().divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
            residue = request.totalAmount().subtract(base.multiply(BigDecimal.valueOf(count)));
        }

        UUID groupId = UUID.randomUUID();
        Instant now = Instant.now();
        List<Transaction> installments = new ArrayList<>();

        for (int i = first; i <= count; i++) {
            Transaction transaction = new Transaction();

            transaction.setAccountId(account.getId());
            transaction.setCategoryId(category.getId());
            transaction.setAmount(hasInstallmentAmount ? request.installmentAmount() : (i == 1 ? base.add(residue) : base));
            transaction.setType(TransactionType.EXPENSE);
            transaction.setDescription(request.description());
            transaction.setDate(request.date().plusMonths(i - first));
            transaction.setInstallmentGroupId(groupId);
            transaction.setInstallmentNumber(i);
            transaction.setInstallmentTotal(count);
            transaction.setCreatedAt(now);
            transaction.setUpdatedAt(now);

            installments.add(transaction);
        }

        return repository.saveAll(installments).stream()
                .map(t -> new TransactionResponse(t, account.getName(), category.getName()))
                .toList();
    }

    @Transactional
    public void deleteInstallmentGroup(UUID groupId) {
        Long userId = userProvider.getCurrentUserId();
        List<Transaction> installments = repository.findAllByInstallmentGroupId(groupId);

        if (installments.isEmpty()
                || accountRepository.findByIdAndUserId(installments.get(0).getAccountId(), userId).isEmpty())
            throw new InstallmentGroupNotFoundException(groupId);

        if (repository.existsByRefundOfTransactionIdIn(installments.stream().map(Transaction::getId).toList()))
            throw new TransactionIsRefundedException(installments.get(0).getId());

        repository.deleteAll(installments);
    }

    /** Spec 028, regras 7 a 10: cria uma receita de estorno por compra (ou parcela) do alcance, na data de cada uma. */
    @Transactional
    public List<TransactionResponse> refundTransaction(Long id, RefundRequest request) {
        Transaction purchase = this.findOwnedTransaction(id);
        Account account = accountRepository.findById(purchase.getAccountId()).orElseThrow(() -> new AccountNotFoundException(purchase.getAccountId()));
        RefundScope scope = request.scope() == null ? RefundScope.ONLY_THIS : request.scope();
        boolean installment = purchase.getInstallmentGroupId() != null;

        if (account.getType() != AccountType.CREDIT_CARD
                || purchase.getType() != TransactionType.EXPENSE
                || purchase.getTransferGroupId() != null
                || purchase.getRefundOfTransactionId() != null
                || billRepository.existsByTransactionId(id))
            throw new TransactionNotRefundableException(id);

        if (scope != RefundScope.ONLY_THIS && !installment)
            throw new InvalidInstallmentRequestException("scope", "O alcance de grupo só vale para compra parcelada");

        List<Transaction> targets = switch (scope) {
            case ONLY_THIS -> List.of(purchase);
            case THIS_AND_FOLLOWING -> repository.findAllByInstallmentGroupIdAndInstallmentNumberGreaterThanEqual(purchase.getInstallmentGroupId(), purchase.getInstallmentNumber());
            case WHOLE_PURCHASE -> repository.findAllByInstallmentGroupId(purchase.getInstallmentGroupId());
        };

        Set<Long> alreadyRefunded = repository.findAllByRefundOfTransactionIdIn(targets.stream().map(Transaction::getId).toList())
                .stream().map(Transaction::getRefundOfTransactionId).collect(Collectors.toSet());

        List<Transaction> pending = targets.stream()
                .filter(t -> !alreadyRefunded.contains(t.getId()))
                .sorted(Comparator.comparing(t -> t.getInstallmentNumber() == null ? 0 : t.getInstallmentNumber()))
                .toList();

        if (pending.isEmpty())
            throw new TransactionIsRefundedException(id);

        Category category = categoryService.findRefundCategory();
        Instant now = Instant.now();
        List<Transaction> refunds = new ArrayList<>();

        for (Transaction item : pending) {
            Transaction refund = new Transaction();

            refund.setAccountId(item.getAccountId());
            refund.setCategoryId(category.getId());
            refund.setAmount(item.getAmount());
            refund.setType(TransactionType.INCOME);
            refund.setDescription("Estorno: " + (item.getDescription() == null ? "" : item.getDescription()));
            refund.setDate(request.date().isAfter(item.getDate()) ? request.date() : item.getDate());
            refund.setRefundOfTransactionId(item.getId());
            refund.setCreatedAt(now);
            refund.setUpdatedAt(now);

            refunds.add(refund);
        }

        try {
            return repository.saveAllAndFlush(refunds).stream()
                    .map(t -> new TransactionResponse(t, account.getName(), category.getName()))
                    .toList();
        } catch (DataIntegrityViolationException ex) {
            // Pedido simultâneo: o índice único garante um estorno por compra.
            throw new TransactionIsRefundedException(id);
        }
    }
}
