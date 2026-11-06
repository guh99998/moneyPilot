package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.AccountNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidInvoiceDaysException;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;
    private final AuthenticatedUserProvider userProvider;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public AccountResponse createAccount(AccountRequest request) {
        Account account = new Account();

        account.setUserId(userProvider.getCurrentUserId());

        account.setName(request.name());
        account.setType(request.accountType());
        account.setInitialBalance(request.initialBalance());
        account.setCountsAsAvailable(request.countsAsAvailable() != null
                ? request.countsAsAvailable()
                : request.accountType().countsAsAvailableByDefault());
        this.validateInvoiceDays(request);
        if (request.closingDay() != null) {
            this.applyInvoiceDays(account, request);
        }
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());

        return new AccountResponse(repository.save(account));
    }

    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        Long userId = userProvider.getCurrentUserId();
        return repository.findAllByUserId(userId, pageable).map(AccountResponse::new);
    }

    public AccountResponse getAccountById(Long accountId) {
        Account account = this.findOwnedAccount(accountId);

        return new AccountResponse(account);
    }

    private Account findOwnedAccount(Long accountId) {
        Long userId = userProvider.getCurrentUserId();

        return repository.findByIdAndUserId(accountId, userId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public AccountResponse updateAccountById(Long id, AccountRequest request) {
        Account account = this.findOwnedAccount(id);

        AccountType previousType = account.getType();

        account.setName(request.name());
        account.setType(request.accountType());
        account.setInitialBalance(request.initialBalance());
        if (request.countsAsAvailable() != null) {
            account.setCountsAsAvailable(request.countsAsAvailable());
        } else if (previousType != request.accountType()) {
            account.setCountsAsAvailable(request.accountType().countsAsAvailableByDefault());
        }
        this.validateInvoiceDays(request);
        if (request.accountType() != AccountType.CREDIT_CARD) {
            account.setClosingDay(null);
            account.setDueDay(null);
            account.setInvoicesFrom(null);
        } else if (request.closingDay() != null) {
            this.applyInvoiceDays(account, request);
        }
        account.setUpdatedAt(Instant.now());

        return new AccountResponse(repository.save(account));
    }

    /** Spec 016, regras 1 e 2: os dois dias juntos, só em cartão; a primeira vez tira o cartão do disponível e marca o início das faturas. */
    private void applyInvoiceDays(Account account, AccountRequest request) {
        boolean firstTime = account.getClosingDay() == null;

        account.setClosingDay(request.closingDay());
        account.setDueDay(request.dueDay());
        if (firstTime) {
            account.setInvoicesFrom(new InvoiceCycle(request.closingDay(), request.dueDay()).cycleOf(LocalDate.now(clock)).start());
            if (request.countsAsAvailable() == null) {
                account.setCountsAsAvailable(false);
            }
        }
    }

    private void validateInvoiceDays(AccountRequest request) {
        if (request.closingDay() == null && request.dueDay() == null) {
            return;
        }
        if (request.accountType() != AccountType.CREDIT_CARD) {
            throw new InvalidInvoiceDaysException("closingDay", "Fechamento e vencimento só valem para cartão de crédito");
        }
        if (request.closingDay() == null) {
            throw new InvalidInvoiceDaysException("closingDay", "Informe o dia de fechamento junto com o de vencimento");
        }
        if (request.dueDay() == null) {
            throw new InvalidInvoiceDaysException("dueDay", "Informe o dia de vencimento junto com o de fechamento");
        }
    }

    public void deleteAccountById(Long id) {
        Account account = this.findOwnedAccount(id);

        repository.delete(account);
    }

    public BalanceResponse getAccountBalance(Long id) {
        Account account = this.findOwnedAccount(id);

        BigDecimal accountBalance = transactionRepository.getAccountBalance(account.getId());
        Long counting = transactionRepository.countAllByAccountId(account.getId());
        BigDecimal currentBalance = account.getInitialBalance().add(accountBalance);

        return new BalanceResponse(account.getId(), account.getInitialBalance(), currentBalance, counting);
    }
}
