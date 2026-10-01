package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.Category;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryService;
import br.com.desenvolvedorgustavolopes.moneyPilot.category.CategoryType;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BillRecurrenceService {

    private final BillRecurrenceRepository repository;
    private final AuthenticatedUserProvider userProvider;
    private final CategoryService categoryService;
    private final AccountRepository accountRepository;
    private final BillRepository billRepository;

    private BillRecurrence findOwnedRecurrence(Long id) {
        Long userId = userProvider.getCurrentUserId();
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> new BillRecurrenceNotFoundException(id));
    }

    private void verifyCategoryBillType(Category category, BillType type) {
        if (category.getType().name().equals(CategoryType.INCOME.name()))
            if(!(type.name().equals(BillType.RECEIVABLE.name())))
                throw new CategoryTypeMismatchException(category.getId(), type);

        if (category.getType().name().equals(CategoryType.EXPENSE.name()))
            if(!(type.name().equals(BillType.PAYABLE.name())))
                throw new CategoryTypeMismatchException(category.getId(), type);
    }

    private void verifyDateRange(LocalDate endDate, LocalDate startDate) {
        if(endDate != null && !(endDate.isAfter(startDate)))
            throw new InvalidRecurrenceDateRangeException(endDate, startDate);
    }

    private String getAccountName(Long accountId) {
        if(accountId == null)
            return null;

        Long userId = userProvider.getCurrentUserId();

        return accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(() -> new AccountNotFoundException(accountId)).getName();
    }

    private BillRecurrenceResponse toResponse(BillRecurrence billRecurrence) {
        Category category = categoryService.findVisibleCategory(billRecurrence.getCategoryId());
        String accountName = this.getAccountName(billRecurrence.getAccountId());
        return new BillRecurrenceResponse(billRecurrence, accountName, category.getName());
    }

    public BillRecurrenceResponse createBillRecurrence(BillRecurrenceRequest request) {
        Long userId = userProvider.getCurrentUserId();
        Instant now = Instant.now();

        this.verifyDateRange(request.endDate(), request.startDate());
        String accountName = this.getAccountName(request.accountId());

        Category category = categoryService.findVisibleCategory(request.categoryId());
        this.verifyCategoryBillType(category, request.type());

        BillRecurrence billRecurrence = new BillRecurrence();

        billRecurrence.setUserId(userId);
        billRecurrence.setAccountId(request.accountId());
        billRecurrence.setCategoryId(request.categoryId());
        billRecurrence.setDescription(request.description());
        billRecurrence.setAmount(request.amount());
        billRecurrence.setType(request.type());
        billRecurrence.setDayOfMonth(request.dayOfMonth());
        billRecurrence.setStartDate(request.startDate());
        billRecurrence.setEndDate(request.endDate());
        billRecurrence.setActive(true);
        billRecurrence.setCreatedAt(now);
        billRecurrence.setUpdatedAt(now);

        return new BillRecurrenceResponse(repository.save(billRecurrence), accountName, category.getName());
    }

    public BillRecurrenceResponse getBillRecurrenceById(Long id) {
        BillRecurrence billRecurrence = this.findOwnedRecurrence(id);
        return toResponse(billRecurrence);
    }

    public Page<BillRecurrenceResponse> getAllBillRecurrences(Pageable pageable) {
        Long userId = userProvider.getCurrentUserId();

        return repository.findAllByUserId(userId, pageable)
                .map(this::toResponse);
    }

    public BillRecurrenceResponse updateBillRecurrence(Long id, BillRecurrenceRequest request) {
        BillRecurrence billRecurrence = this.findOwnedRecurrence(id);
        this.verifyDateRange(request.endDate(), request.startDate());
        Category category = categoryService.findVisibleCategory(request.categoryId());
        this.verifyCategoryBillType(category, request.type());
        this.getAccountName(request.accountId());

        billRecurrence.setAccountId(request.accountId());
        billRecurrence.setCategoryId(request.categoryId());
        billRecurrence.setDescription(request.description());
        billRecurrence.setAmount(request.amount());
        billRecurrence.setType(request.type());
        billRecurrence.setDayOfMonth(request.dayOfMonth());
        billRecurrence.setStartDate(request.startDate());
        billRecurrence.setEndDate(request.endDate());
        billRecurrence.setUpdatedAt(Instant.now());

        return toResponse(repository.save(billRecurrence));
    }

    public void deleteBillRecurrence(Long id) {
        BillRecurrence billRecurrence = this.findOwnedRecurrence(id);
        if(billRepository.existsByRecurrenceId(id))
            throw new BillRecurrenceHasBillsException(id);

        repository.delete(billRecurrence);
    }

    public BillRecurrenceResponse deactivate(Long id) {
        BillRecurrence billRecurrence = this.findOwnedRecurrence(id);
        billRecurrence.setActive(false);
        billRecurrence.setUpdatedAt(Instant.now());

        return toResponse(repository.save(billRecurrence));
    }
}
