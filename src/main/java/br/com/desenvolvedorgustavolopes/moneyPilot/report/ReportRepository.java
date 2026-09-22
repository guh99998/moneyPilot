package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

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
}
