package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.Account;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.Category;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryService;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryType;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.*;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.Transaction;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository repository;
    private final AuthenticatedUserProvider userProvider;
    private final AccountRepository accountRepository;
    private final CategoryService categoryService;
    private final TransactionRepository transactionRepository;
    private final BillRecurrenceRepository billRecurrenceRepository;

    public Page<BillResponse> getAllBills(Long accountId,
                                          Long categoryId,
                                          BillType type,
                                          BillStatusFilter status,
                                          LocalDate dueDateFrom,
                                          LocalDate dueDateTo,
                                          Pageable pageable) {
        Long userId = userProvider.getCurrentUserId();
        BillStatus queryStatus = null;
        LocalDate dueBefore = null;

        if (status != null) {
            if (status == BillStatusFilter.OVERDUE) {
                queryStatus = BillStatus.OPEN;
                dueBefore = LocalDate.now();
            } else {
                queryStatus = BillStatus.valueOf(status.name());
            }
        }

        if (accountId != null) {
            accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(
                    () -> new AccountNotFoundException(accountId)
            );
        }

        return repository.findAllFiltered(
                userId, type, queryStatus, accountId, categoryId, dueDateFrom, dueDateTo, dueBefore, pageable
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

        if (request.accountId() != null) {
            accountName = accountRepository.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new AccountNotFoundException(request.accountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(request.categoryId());

        if (request.type().name().equals(BillType.PAYABLE.name()))
            if (category.getType().name().equals(CategoryType.INCOME.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        if (request.type().name().equals(BillType.RECEIVABLE.name()))
            if (category.getType().name().equals(CategoryType.EXPENSE.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        Bill bill = new Bill();

        bill.setUserId(userId);
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

        if (bill.getAccountId() != null) {
            accountName = accountRepository.findByIdAndUserId(bill.getAccountId(), bill.getUserId())
                    .orElseThrow(() -> new AccountNotFoundException(bill.getAccountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(bill.getCategoryId());

        return new BillResponse(bill, accountName, category.getName());
    }

    public BillResponse updateBill(Long id, BillRequest request) {
        Bill bill = this.findOwnedBill(id);
        Long userId = userProvider.getCurrentUserId();

        if (bill.getStatus() != BillStatus.OPEN)
            throw new BillNotOpenException(id);

        String accountName = null;

        if (request.accountId() != null) {
            accountName = accountRepository.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new AccountNotFoundException(request.accountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(request.categoryId());

        if (request.type().name().equals(BillType.PAYABLE.name()))
            if (category.getType().name().equals(CategoryType.INCOME.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        if (request.type().name().equals(BillType.RECEIVABLE.name()))
            if (category.getType().name().equals(CategoryType.EXPENSE.name()))
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

        if (bill.getStatus() != BillStatus.OPEN)
            throw new BillNotOpenException(id);

        repository.delete(bill);
    }

    public BillResponse cancel(Long id) {
        Bill bill = this.findOwnedBill(id);

        if (bill.getStatus() != BillStatus.OPEN)
            throw new BillNotOpenException(id);

        String accountName = null;

        if (bill.getAccountId() != null) {
            accountName = accountRepository.findByIdAndUserId(bill.getAccountId(), bill.getUserId())
                    .orElseThrow(() -> new AccountNotFoundException(bill.getAccountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(bill.getCategoryId());

        bill.setStatus(BillStatus.CANCELED);
        bill.setUpdatedAt(Instant.now());

        return new BillResponse(repository.save(bill), accountName, category.getName());
    }

    @Transactional
    public BillResponse settle(Long id, SettleRequest request) {
        Bill bill = this.findOwnedBill(id);
        Long userId = userProvider.getCurrentUserId();

        if (bill.getStatus() != BillStatus.OPEN)
            throw new BillNotOpenException(id);

        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId).orElseThrow(() -> new AccountNotFoundException(request.accountId()));
        Category category = categoryService.findVisibleCategory(bill.getCategoryId());

        Transaction transaction = new Transaction();
        transaction.setAccountId(request.accountId());
        transaction.setCategoryId(bill.getCategoryId());
        transaction.setAmount(request.amount());
        transaction.setType(bill.getType().name().equals(BillType.PAYABLE.name()) ? TransactionType.EXPENSE : TransactionType.INCOME);
        transaction.setDescription(bill.getDescription());
        transaction.setDate(LocalDate.now());
        transaction.setCreatedAt(Instant.now());
        transaction.setUpdatedAt(Instant.now());

        transactionRepository.save(transaction);

        if(repository.settleIfOpen(bill.getId(), userId, BillStatus.OPEN, Instant.now(), request.amount(), transaction.getId(), BillStatus.SETTLED, request.accountId(), Instant.now()) == 0)
            throw new BillNotOpenException(id);

        Bill newBill = repository.findByIdAndUserId(id, userId).orElseThrow(() -> new BillNotFoundException(id));

        return new BillResponse(newBill, account.getName(), category.getName());
    }

    @Transactional
    public BillResponse unsettle(Long id) {
        Bill bill = this.findOwnedBill(id);
        String accountName = null;

        if(bill.getStatus() != BillStatus.SETTLED)
            throw new BillNotSettledException(id);

        Long transactionId = bill.getTransactionId();

        if(bill.getAccountId() != null) {
            accountName = accountRepository.findByIdAndUserId(bill.getAccountId(), bill.getUserId()).orElseThrow(() -> new AccountNotFoundException(bill.getAccountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(bill.getCategoryId());

        bill.setStatus(BillStatus.OPEN);
        bill.setSettledAt(null);
        bill.setSettledAmount(null);
        bill.setTransactionId(null);
        bill.setUpdatedAt(Instant.now());

        Bill newBill = repository.save(bill);

        transactionRepository.deleteById(transactionId);

        return new BillResponse(newBill, accountName, category.getName());
    }

    @Transactional
    public List<BillResponse> createInstallmentPlan(InstallmentPlanRequest request) {
        Long userId = userProvider.getCurrentUserId();
        String accountName = null;

        if (request.accountId() != null) {
            accountName = accountRepository.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new AccountNotFoundException(request.accountId())).getName();
        }

        Category category = categoryService.findVisibleCategory(request.categoryId());

        if (request.type().name().equals(BillType.PAYABLE.name()))
            if (category.getType().name().equals(CategoryType.INCOME.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        if (request.type().name().equals(BillType.RECEIVABLE.name()))
            if (category.getType().name().equals(CategoryType.EXPENSE.name()))
                throw new CategoryTypeMismatchException(request.categoryId(), request.type());

        if (request.totalAmount().compareTo(BigDecimal.valueOf(request.installments(), 2)) < 0)
            throw new InstallmentAmountTooSmallException(BigDecimal.valueOf(request.installments(), 2), request.installments());

        BigDecimal base = request.totalAmount().divide(BigDecimal.valueOf(request.installments()), 2, RoundingMode.DOWN);
        BigDecimal residue = request.totalAmount().subtract(base.multiply(BigDecimal.valueOf(request.installments())));
        BigDecimal first = base.add(residue);

        UUID groupId = UUID.randomUUID();
        Instant now = Instant.now();
        List<Bill> bills = new ArrayList<>();

        for (int i = 1; i <= request.installments(); i++) {
            Bill bill = new Bill();

            bill.setUserId(userId);
            bill.setAccountId(request.accountId());
            bill.setCategoryId(request.categoryId());
            bill.setDescription(request.description());
            bill.setType(request.type());
            bill.setStatus(BillStatus.OPEN);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);
            bill.setAmount(i == 1 ? first : base);
            bill.setDueDate(request.firstDueDate().plusMonths(i - 1));
            bill.setInstallmentGroupId(groupId);
            bill.setInstallmentNumber(i);
            bill.setInstallmentTotal(request.installments());

            bills.add(bill);
        }

        List<BillResponse> responses = new ArrayList<>();

        for(Bill bill : repository.saveAll(bills)) {
            responses.add(new BillResponse(bill, accountName, category.getName()));
        }

        return responses;
    }

    @Transactional
    public void deleteInstallmentGroup(UUID groupId) {
        Long userId = userProvider.getCurrentUserId();
        List<Bill> bills = repository.findAllByInstallmentGroupIdAndUserIdOrderByInstallmentNumberAsc(groupId, userId);

        if(bills.isEmpty())
            throw new InstallmentGroupNotFoundException(groupId);

        for (Bill bill : bills) {
            if (bill.getStatus() == BillStatus.SETTLED)
                throw new InstallmentGroupHasSettledBillException(bill.getInstallmentNumber(), bill.getInstallmentTotal(), bill.getId());
        }

        repository.deleteAll(bills);
    }

    @Transactional
    public List<BillResponse> bulkSettle(BulkSettleRequest request) {
        LinkedHashSet<Long> billIds = new LinkedHashSet<>(request.billIds());
        List<BillResponse> results = new ArrayList<>();

        for (Long id : billIds) {
            Bill bill = this.findOwnedBill(id);
            results.add(this.settle(id, new SettleRequest(request.accountId(), bill.getAmount())));
        }

        return results;
    }

    @Transactional
    public List<BillResponse> materializeFromRecurrences(YearMonth yearMonth) {
        Long userId = userProvider.getCurrentUserId();
        List<BillRecurrence> billRecurrences = billRecurrenceRepository.findAllByUserIdAndActiveTrue(userId);
        List<Bill> bills = new ArrayList<>();
        Instant now = Instant.now();
        for(BillRecurrence billRecurrence : billRecurrences) {
            LocalDate dueDate = this.dueDateFor(yearMonth, billRecurrence.getDayOfMonth());
            if(dueDate.isBefore(billRecurrence.getStartDate()))
                continue;

            if(billRecurrence.getEndDate() != null && dueDate.isAfter(billRecurrence.getEndDate()))
                continue;

            if (repository.existsByRecurrenceIdAndDueDate(billRecurrence.getId(), dueDate))
                continue;

            Bill bill = new Bill();
            bill.setUserId(userId);
            bill.setAccountId(billRecurrence.getAccountId());
            bill.setCategoryId(billRecurrence.getCategoryId());
            bill.setDescription(billRecurrence.getDescription());
            bill.setAmount(billRecurrence.getAmount());
            bill.setType(billRecurrence.getType());
            bill.setDueDate(dueDate);
            bill.setRecurrenceId(billRecurrence.getId());
            bill.setStatus(BillStatus.OPEN);
            bill.setCreatedAt(now);
            bill.setUpdatedAt(now);

            bills.add(bill);
        }

        List<BillResponse> responses = new ArrayList<>();

        for(Bill bill : repository.saveAll(bills)) {
            String accountName = null;
            if (bill.getAccountId() != null)
                accountName = accountRepository.findByIdAndUserId(bill.getAccountId(), bill.getUserId()).orElseThrow(() -> new AccountNotFoundException(bill.getAccountId())).getName();

            Category category = categoryService.findVisibleCategory(bill.getCategoryId());

            responses.add(new BillResponse(bill, accountName, category.getName()));
        }

        return responses;
    }

    private LocalDate dueDateFor(YearMonth yearMonth, Integer dayOfMonth) {
        int days = yearMonth.lengthOfMonth();
        int minDay = Math.min(days, dayOfMonth);
        return yearMonth.atDay(minDay);
    }
}
