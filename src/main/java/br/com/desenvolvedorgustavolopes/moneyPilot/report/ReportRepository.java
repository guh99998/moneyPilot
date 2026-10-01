package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface ReportRepository extends Repository<Transaction, Long> {
    @Query("""
    SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS totalIncome,
        COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS totalExpense
    FROM Transaction t, Account a
    WHERE t.accountId = a.id
        AND a.userId = :userId
        AND t.transferGroupId IS NULL
        AND t.date >= :from
        AND t.date < :nextMonth
    """)
    MonthlyTotals findMonthlyTotals(@Param("userId") Long userId,
                                    @Param("from")LocalDate from,
                                    @Param("nextMonth") LocalDate nextMonth);

    @Query("""
    SELECT c.id AS categoryId,
        c.name AS categoryName,
        SUM(t.amount) AS total
    FROM Transaction t, Account a, Category c
    WHERE t.accountId = a.id
        AND t.categoryId = c.id
        AND a.userId = :userId
        AND t.type = 'EXPENSE'
        AND t.transferGroupId IS NULL
        AND t.date >= :from
        AND t.date < :nextMonth
    GROUP BY c.id, c.name
    ORDER BY SUM(t.amount) DESC
    """)
    List<CategorySpending> findSpendingByCategory(@Param("userId") Long userId,
                                                  @Param("from") LocalDate from,
                                                  @Param("nextMonth") LocalDate nextMonth);

    @Query("""
    SELECT c.id AS categoryId,
        c.name AS categoryName,
        b.amountLimit AS budgetedAmount,
        COALESCE((SELECT SUM(t.amount)
                  FROM Transaction t, Account a
                  WHERE t.accountId = a.id
                      AND a.userId = :userId
                      AND t.categoryId = b.categoryId
                      AND t.type = 'EXPENSE'
                      AND t.transferGroupId IS NULL
                      AND t.date >= :from
                      AND t.date < :nextMonth), 0) AS spentAmount
    FROM Budget b, Category c
    WHERE b.categoryId = c.id
        AND b.userId = :userId
        AND b.month = :month
        AND b.year = :year
    ORDER BY c.name
    """)
    List<BudgetVsActual> findBudgetVsActual(@Param("userId") Long userId,
                                            @Param("month") Integer month,
                                            @Param("year") Integer year,
                                            @Param("from") LocalDate from,
                                            @Param("nextMonth") LocalDate nextMonth);

    @Query("""
    SELECT
        COALESCE((SELECT SUM(a.initialBalance) FROM Account a WHERE a.userId = :userId), 0)
         +
        COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END) FROM Transaction t, Account a WHERE t.accountId = a.id AND a.userId = :userId), 0)
    """)
    BigDecimal getTotalBalance(@Param("userId") Long userId);

    @Query("""
    SELECT b.dueDate AS dueDate,
        COUNT(b) AS billCount,
        COALESCE(SUM(CASE WHEN b.type = 'PAYABLE' THEN b.amount ELSE 0 END), 0) AS totalPayable,
        COALESCE(SUM(CASE WHEN b.type = 'RECEIVABLE' THEN b.amount ELSE 0 END), 0) AS totalReceivable
    FROM Bill b
    WHERE b.userId = :userId
        AND b.status = 'OPEN'
        AND b.dueDate <= :to
    GROUP BY b.dueDate
    ORDER BY b.dueDate
    """)
    List<BillDueTotals> findOpenBillTotalsByDueDate(@Param("userId") Long userId,
                                                    @Param("to") LocalDate to);

    @Query("""
    SELECT COUNT(b) FILTER (WHERE b.status = 'OPEN' AND b.type = 'PAYABLE') AS payableCount,
        COALESCE(SUM(b.amount) FILTER (WHERE b.status = 'OPEN' AND b.type = 'PAYABLE'), 0) AS payableTotal,
        COUNT(b) FILTER (WHERE b.status = 'OPEN' AND b.type = 'PAYABLE' AND b.dueDate < :today) AS payableOverdueCount,
        COALESCE(SUM(b.amount) FILTER (WHERE b.status = 'OPEN' AND b.type = 'PAYABLE' AND b.dueDate < :today), 0) AS payableOverdueTotal,
        COUNT(b) FILTER (WHERE b.status = 'OPEN' AND b.type = 'RECEIVABLE') AS receivableCount,
        COALESCE(SUM(b.amount) FILTER (WHERE b.status = 'OPEN' AND b.type = 'RECEIVABLE'), 0) AS receivableTotal,
        COUNT(b) FILTER (WHERE b.status = 'OPEN' AND b.type = 'RECEIVABLE' AND b.dueDate < :today) AS receivableOverdueCount,
        COALESCE(SUM(b.amount) FILTER (WHERE b.status = 'OPEN' AND b.type = 'RECEIVABLE' AND b.dueDate < :today), 0) AS receivableOverdueTotal,
        COUNT(b) FILTER (WHERE b.status = 'SETTLED' AND b.settledAt >= :monthStart AND b.settledAt < :nextMonthStart) AS settledCount,
        COALESCE(SUM(b.settledAmount) FILTER (WHERE b.status = 'SETTLED' AND b.type = 'PAYABLE' AND b.settledAt >= :monthStart AND b.settledAt < :nextMonthStart), 0) AS settledPayable,
        COALESCE(SUM(b.settledAmount) FILTER (WHERE b.status = 'SETTLED' AND b.type = 'RECEIVABLE' AND b.settledAt >= :monthStart AND b.settledAt < :nextMonthStart), 0) AS settledReceivable
    FROM Bill b
    WHERE b.userId = :userId
    """)
    BillsSummaryTotals findBillsSummary(@Param("userId") Long userId,
                                        @Param("today") LocalDate today,
                                        @Param("monthStart") Instant monthStart,
                                        @Param("nextMonthStart") Instant nextMonthStart);
}
