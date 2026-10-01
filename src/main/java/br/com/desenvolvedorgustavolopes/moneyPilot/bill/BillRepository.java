package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByIdAndUserId(Long id, Long userId);

    @Query("""
    SELECT b
    FROM Bill b
    WHERE b.userId = :userId
         AND (:type IS NULL OR b.type = :type)
         AND (:status IS NULL OR b.status = :status)
         AND (:categoryId IS NULL OR b.categoryId = :categoryId)
         AND (:accountId IS NULL OR b.accountId = :accountId)
         AND b.dueDate >= COALESCE(:dueDateFrom, b.dueDate) and b.dueDate <= COALESCE(:dueDateTo, b.dueDate)
         AND (CAST(:dueBefore AS LocalDate) IS NULL OR b.dueDate < :dueBefore)
    """)
    Page<Bill> findAllFiltered(@Param("userId") Long userId,
                               @Param("type") BillType type,
                               @Param("status") BillStatus status,
                               @Param("accountId") Long accountId,
                               @Param("categoryId") Long categoryId,
                               @Param("dueDateFrom") LocalDate dueDateFrom,
                               @Param("dueDateTo") LocalDate dueDateTo,
                               @Param("dueBefore") LocalDate dueBefore,
                               Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
    UPDATE Bill b
    SET b.status = :status, b.settledAt = :settledAt, b.settledAmount = :settledAmount, b.transactionId = :transactionId, b.accountId = :accountId, b.updatedAt = :updatedAt
    WHERE b.id = :id AND b.userId = :userId AND b.status = :expectedStatus
    """)
    int settleIfOpen(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("expectedStatus") BillStatus expectedStatus,
                     @Param("settledAt") Instant settledAt,
                     @Param("settledAmount") BigDecimal settledAmount,
                     @Param("transactionId") Long transactionId,
                     @Param("status") BillStatus status,
                     @Param("accountId") Long accountId,
                     @Param("updatedAt") Instant updatedAt);

    List<Bill> findAllByInstallmentGroupIdAndUserIdOrderByInstallmentNumberAsc(UUID installmentGroupId, Long userId);

    boolean existsByTransactionId(Long transactionId);

    boolean existsByRecurrenceId(Long recurrenceId);

    boolean existsByRecurrenceIdAndDueDate(Long recurrenceId, LocalDate dueDate);
}
