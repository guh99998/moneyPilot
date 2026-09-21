package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.AccountNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;
    private final AuthenticatedUserProvider userProvider;
    private final TransactionRepository transactionRepository;

    public AccountResponse createAccount(AccountRequest request) {
        Account account = new Account();

        account.setUserId(userProvider.getCurrentUserId());

        account.setName(request.name());
        account.setType(request.accountType());
        account.setInitialBalance(request.initialBalance());
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

        account.setName(request.name());
        account.setType(request.accountType());
        account.setInitialBalance(request.initialBalance());
        account.setUpdatedAt(Instant.now());

        return new AccountResponse(repository.save(account));
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
