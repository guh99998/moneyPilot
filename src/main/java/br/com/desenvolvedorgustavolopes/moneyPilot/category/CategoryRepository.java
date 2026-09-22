package br.com.desenvolvedorgustavolopes.moneyPilot.category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("""
        SELECT c
        FROM Category c
        WHERE c.userId = :userId
            OR c.userId IS NULL
    """)
    Page<Category> findVisibleToUser(@Param("userId") Long userId, Pageable pageable);
}
