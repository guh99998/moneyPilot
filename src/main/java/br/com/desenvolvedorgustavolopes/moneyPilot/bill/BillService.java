package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.Account;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.Category;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryService;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryType;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.AccountNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.BillNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.BillNotOpenException;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.CategoryTypeMismatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository repository;
    private final AuthenticatedUserProvider userProvider;
    private final AccountRepository accountRepository;
    private final CategoryService categoryService;

    public Page<BillResponse> getAllBills(Long accountId,
                                          Long categoryId,
                                          BillType type,
                                          BillStatus status,
                                          LocalDate dueDateFrom,
                                          LocalDate dueDateTo,
                                          Pageable pageable) {
        Long userId = userProvider.getCurrentUserId();

        if(accountId != null) {
            accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(
                    () -> new AccountNotFoundException(accountId)
            );
        }

        return repository.findAllFiltered(
                userId, type, status, accountId, categoryId, dueDateFrom, dueDateTo, pageable
        ).map(
                b -> {
                    Category category = categoryService.findVisibleCategory(b.getCategoryId());
                    String accountName;
                    if (b.getAccountId() != null) {
                        Account account = accountRepository.findByIdAndUserId(b.getAccountId(), b.getUserId())
                                .orElseThrow(() -> new AccountNotFoundException(b.getAccountId()));
                        accountName = account.getName();
                    } else {
                        accountName = null;
                    }

                    return new BillResponse(b, accountName, category.getName());
                }
        );
    }

    private Bill findOwnedBill(Long id) {
        Long userId = userProvider.getCurrentUserId();
        Bill bill = repository.findById(id).orElseThrow(() -> new BillNotFoundException(id));
        if (!bill.getUserId().equals(userId))
            throw new BillNotFoundException(id);

        return bill;
    }

    public BillResponse createBill(BillRequest request) {
        Long userId = userProvider.getCurrentUserId();
        String accountName = null;

        if(request.accountId() != null) {
            accountName = accountRepository.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new AccountNotFoundException(request.accountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(request.categoryId());

        if(request.type().name().equals(BillType.PAYABLE.name()))
            if(category.getType().name().equals(CategoryType.INCOME.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        if(request.type().name().equals(BillType.RECEIVABLE.name()))
            if(category.getType().name().equals(CategoryType.EXPENSE.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        Bill bill = new Bill();

        bill.setAccountId(request.accountId());
        bill.setCategoryId(request.categoryId());
        bill.setDescription(request.description());
        bill.setAmount(request.amount());
        bill.setDueDate(request.dueDate());
        bill.setType(request.type());
        bill.setCreatedAt(Instant.now());
        bill.setUpdatedAt(Instant.now());
        bill.setStatus(BillStatus.OPEN);

        return new BillResponse(repository.save(bill), accountName, category.getName());
    }

    public BillResponse getBillById(Long id) {
        Bill bill = this.findOwnedBill(id);
        String accountName = null;

        if(bill.getAccountId() != null) {
            accountName = accountRepository.findByIdAndUserId(bill.getAccountId(), bill.getUserId())
                    .orElseThrow(() -> new AccountNotFoundException(bill.getAccountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(bill.getCategoryId());

        return new BillResponse(bill, accountName, category.getName());
    }

    public BillResponse updateBill(Long id, BillRequest request) {
        Bill bill = this.findOwnedBill(id);
        Long userId = userProvider.getCurrentUserId();

        String accountName = null;

        if(request.accountId() != null) {
            accountName = accountRepository.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new AccountNotFoundException(request.accountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(request.categoryId());

        if(request.type().name().equals(BillType.PAYABLE.name()))
            if(category.getType().name().equals(CategoryType.INCOME.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        if(request.type().name().equals(BillType.RECEIVABLE.name()))
            if(category.getType().name().equals(CategoryType.EXPENSE.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        bill.setAccountId(request.accountId());
        bill.setCategoryId(request.categoryId());
        bill.setDescription(request.description());
        bill.setAmount(request.amount());
        bill.setDueDate(request.dueDate());
        bill.setType(request.type());
        bill.setUpdatedAt(Instant.now());

        return new BillResponse(repository.save(bill), accountName, category.getName());
    }

    public void deleteBill(Long id) {
        Bill bill = this.findOwnedBill(id);

        repository.delete(bill);
    }

    public BillResponse cancel(Long id) {
        Bill bill = this.findOwnedBill(id);

        if(bill.getStatus() != BillStatus.OPEN)
            throw new BillNotOpenException(id);

        String accountName = null;

        if(bill.getAccountId() != null) {
            accountName = accountRepository.findByIdAndUserId(bill.getAccountId(), bill.getUserId())
                    .orElseThrow(() -> new AccountNotFoundException(bill.getAccountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(bill.getCategoryId());

        bill.setStatus(BillStatus.CANCELED);
        bill.setUpdatedAt(Instant.now());

        return new BillResponse(repository.save(bill), accountName, category.getName());
    }
}
