package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DismissedAlertRepository extends JpaRepository<DismissedAlert, Long> {

    List<DismissedAlert> findAllByUserId(Long userId);

    Optional<DismissedAlert> findByUserIdAndAlertKey(Long userId, String alertKey);

    /** Exclusão de conta (spec 012): apaga em lote, sem carregar entidades. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DismissedAlert d WHERE d.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
