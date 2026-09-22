package br.com.desenvolvedorgustavolopes.moneyPilot.budget;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    @Query("""
    SELECT b
    FROM Budget b
    WHERE b.userId = :userId and (:categoryId IS NULL OR b.categoryId = :categoryId) and (:month IS NULL OR b.month = :month) and (:year IS NULL OR b.year = :year)
    """)
    Page<Budget> findAllFiltered(@Param("userId") Long userId,
                                 @Param("categoryId") Long categoryId,
                                 @Param("month") Integer month,
                                 @Param("year") Integer year,
                                 Pageable pageable
    );
}
