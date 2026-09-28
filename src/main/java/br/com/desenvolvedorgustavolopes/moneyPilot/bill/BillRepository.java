package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

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
    """)
    Page<Bill> findAllFiltered(@Param("userId") Long userId,
                               @Param("type") BillType type,
                               @Param("status") BillStatus status,
                               @Param("accountId") Long accountId,
                               @Param("categoryId") Long categoryId,
                               @Param("dueDateFrom") LocalDate dueDateFrom,
                               @Param("dueDateTo") LocalDate dueDateTo,
                               Pageable pageable);
}
