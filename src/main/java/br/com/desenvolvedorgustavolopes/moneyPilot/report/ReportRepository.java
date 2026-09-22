package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

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
}
