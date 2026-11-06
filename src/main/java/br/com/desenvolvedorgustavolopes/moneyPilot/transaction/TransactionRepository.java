package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
    SELECT t
    FROM Transaction t, Account a
    WHERE t.accountId = a.id and (:accountId IS NULL OR t.accountId = :accountId) and (:categoryId IS NULL OR t.categoryId = :categoryId) and a.userId = :userId and t.date >= COALESCE(:from, t.date) and t.date <= COALESCE(:to, t.date) and (:type IS NULL OR t.type = :type) and (:transferOnly IS NULL OR t.transferGroupId IS NOT NULL)
    """)
    Page<Transaction> findAllFiltered(@Param("userId") Long userId, @Param("accountId") Long accountId, @Param("categoryId") Long categoryId, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("type") TransactionType type, @Param("transferOnly") Boolean transferOnly, Pageable pageable);

    List<Transaction> findAllByTransferGroupId(UUID transferGroupId);

    @Query("""
    SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0) FROM Transaction t WHERE t.accountId = :accountId
    """)
    BigDecimal getAccountBalance(@Param("accountId") Long id);

    Long countAllByAccountId(Long accountId);

    /** Fatura do cartão (spec 016): totais por dia desde uma data, uma linha por dia com lançamento. */
    @Query("""
    SELECT t.date AS date,
        COALESCE(SUM(CASE WHEN t.transferGroupId IS NULL AND t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS expense,
        COALESCE(SUM(CASE WHEN t.transferGroupId IS NULL AND t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS income,
        COALESCE(SUM(CASE WHEN t.transferGroupId IS NOT NULL AND t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS transferIn
    FROM Transaction t
    WHERE t.accountId = :accountId AND t.date >= :from
    GROUP BY t.date
    ORDER BY t.date
    """)
    List<AccountDayTotals> findDayTotalsSince(@Param("accountId") Long accountId, @Param("from") LocalDate from);

    /** Boas-vindas (spec 013): a pessoa já tem algum lançamento, em qualquer conta? */
    @Query("SELECT COUNT(t) > 0 FROM Transaction t WHERE t.accountId IN (SELECT a.id FROM Account a WHERE a.userId = :userId)")
    boolean existsAnyByUserId(@Param("userId") Long userId);

    /** Exclusão de conta (spec 012): apaga os lançamentos de todas as contas da pessoa. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Transaction t WHERE t.accountId IN (SELECT a.id FROM Account a WHERE a.userId = :userId)")
    int deleteAllByUserId(@Param("userId") Long userId);
}
