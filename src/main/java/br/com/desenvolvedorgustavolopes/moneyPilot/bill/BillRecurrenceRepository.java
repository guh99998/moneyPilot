package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillRecurrenceRepository extends JpaRepository<BillRecurrence, Long> {

    Optional<BillRecurrence> findByIdAndUserId(Long id, Long userId);

    Page<BillRecurrence> findAllByUserId(Long userId, Pageable pageable);

    List<BillRecurrence> findAllByUserIdAndActiveTrue(Long userId);
}
