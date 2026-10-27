package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DismissedAlertRepository extends JpaRepository<DismissedAlert, Long> {

    List<DismissedAlert> findAllByUserId(Long userId);

    Optional<DismissedAlert> findByUserIdAndAlertKey(Long userId, String alertKey);
}
