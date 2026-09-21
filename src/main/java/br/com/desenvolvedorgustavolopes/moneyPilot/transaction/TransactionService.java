package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.Account;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.Category;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryService;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;
    private final AuthenticatedUserProvider userProvider;
    private final AccountRepository accountRepository;
    private final CategoryService categoryService;

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

        return repository
                .findAllFiltered(userId, accountId, categoryId, from, to, queryType, transferOnly, pageableComDesempate).
                map(
                        t -> {Account account = accountRepository.findById(t.getAccountId()).orElseThrow(() -> new AccountNotFoundException(t.getAccountId()));
                            String categoryName;
                            if(t.getCategoryId() != null){
                                Category category = categoryService.findVisibleCategory(t.getCategoryId());
                                categoryName = category.getName();
                            } else {
                                categoryName = null;
                            }
                            return new TransactionResponse(t, account.getName(), categoryName);
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

        return new TransactionResponse(transaction, account.getName(), category.getName());
    }

    public TransactionResponse updateTransaction(Long id, TransactionRequest request) {
        Transaction transaction = this.findOwnedTransaction(id);
        Long userId = userProvider.getCurrentUserId();

        if (transaction.getTransferGroupId() != null)
            throw new TransactionIsTransferException(id);


        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId).orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        Category category = categoryService.findVisibleCategory(request.categoryId());

        if (!category.getType().name().equals(request.type().name()))
            throw new CategoryTypeMismatchException(request.categoryId(), request.type());


        transaction.setAccountId(request.accountId());
        transaction.setCategoryId(request.categoryId());
        transaction.setAmount(request.amount());
        transaction.setType(request.type());
        transaction.setDescription(request.description());
        transaction.setDate(request.date());
        transaction.setUpdatedAt(Instant.now());

        return new TransactionResponse(repository.save(transaction), account.getName(), category.getName());
    }

    public void deleteTransaction(Long id) {
        Transaction transaction = this.findOwnedTransaction(id);

        if (transaction.getTransferGroupId() != null) {
            repository.deleteAll(repository.findAllByTransferGroupId(transaction.getTransferGroupId()));
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
}
