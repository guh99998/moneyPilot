package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

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
    WHERE t.accountId = a.id and (:accountId IS NULL OR t.accountId = :accountId) and (:categoryId IS NULL OR t.categoryId = :categoryId) and a.userId = :userId and (:from IS NULL OR t.date >= :from) and (:to IS NULL OR t.date <= :to) and (:type IS NULL OR t.type = :type) and (:transferOnly IS NULL OR t.transferGroupId IS NOT NULL)
    """)
    Page<Transaction> findAllFiltered(@Param("userId") Long userId, @Param("accountId") Long accountId, @Param("categoryId") Long categoryId, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("type") TransactionType type, @Param("transferOnly") Boolean transferOnly, Pageable pageable);

    List<Transaction> findAllByTransferGroupId(UUID transferGroupId);

    @Query("""
    SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0) FROM Transaction t WHERE t.accountId = :accountId
    """)
    BigDecimal getAccountBalance(@Param("accountId") Long id);

    Long countAllByAccountId(Long accountId);
}
