package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillAlertTotals;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.AlertNotFoundException;
import br.com.desenvolvedorgustavolopes.moneyPilot.report.BudgetVsActual;
import br.com.desenvolvedorgustavolopes.moneyPilot.report.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Os alertas não são gravados: cada chamada recalcula a situação atual do usuário. Só a dispensa fica
 * guardada, junto com uma impressão digital do conteúdo; mudou o conteúdo, o alerta volta.
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    static final int DUE_SOON_DAYS = 7;
    private static final BigDecimal WARNING_RATIO = new BigDecimal("0.8");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AuthenticatedUserProvider userProvider;
    private final BillRepository billRepository;
    private final ReportRepository reportRepository;
    private final DismissedAlertRepository dismissedRepository;
    private final Clock clock;

    private record Computed(AlertResponse alert, String fingerprint) {
    }

    /** Também esquece as dispensas cujo alerta não existe mais: se ele reaparecer, volta a aparecer. */
    @Transactional
    public AlertsResponse listAlerts() {
        Long userId = userProvider.getCurrentUserId();
        List<Computed> computed = compute(userId);

        Set<String> activeKeys = computed.stream().map(c -> c.alert().key()).collect(Collectors.toSet());
        List<DismissedAlert> dismissed = dismissedRepository.findAllByUserId(userId);

        List<DismissedAlert> obsolete = dismissed.stream()
                .filter(d -> !activeKeys.contains(d.getAlertKey()))
                .toList();
        if (!obsolete.isEmpty()) {
            dismissedRepository.deleteAll(obsolete);
        }

        Map<String, String> hidden = new HashMap<>();
        dismissed.stream()
                .filter(d -> activeKeys.contains(d.getAlertKey()))
                .forEach(d -> hidden.put(d.getAlertKey(), d.getFingerprint()));

        List<AlertResponse> visible = computed.stream()
                .filter(c -> !c.fingerprint().equals(hidden.get(c.alert().key())))
                .map(Computed::alert)
                .toList();

        return new AlertsResponse(visible);
    }

    @Transactional
    public void dismiss(String key) {
        Long userId = userProvider.getCurrentUserId();

        Computed current = compute(userId).stream()
                .filter(c -> c.alert().key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AlertNotFoundException(key));

        DismissedAlert dismissed = dismissedRepository.findByUserIdAndAlertKey(userId, key)
                .orElseGet(() -> {
                    DismissedAlert created = new DismissedAlert();
                    created.setUserId(userId);
                    created.setAlertKey(key);
                    return created;
                });

        dismissed.setFingerprint(current.fingerprint());
        dismissed.setDismissedAt(clock.instant());
        dismissedRepository.save(dismissed);
    }

    private List<Computed> compute(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<Computed> result = new ArrayList<>();

        Map<BillType, BillAlertTotals> overdue = byType(billRepository.summarizeOpenDueBefore(userId, today));
        Map<BillType, BillAlertTotals> dueSoon = byType(
                billRepository.summarizeOpenDueBetween(userId, today, today.plusDays(DUE_SOON_DAYS)));

        for (BillType type : BillType.values()) {
            if (overdue.containsKey(type)) {
                result.add(billAlert(AlertKind.BILLS_OVERDUE, type, overdue.get(type), type.name() + "_OVERDUE", false));
            }
        }
        for (BillType type : BillType.values()) {
            if (dueSoon.containsKey(type)) {
                result.add(billAlert(AlertKind.BILLS_DUE_SOON, type, dueSoon.get(type), type.name() + "_DUE_SOON", true));
            }
        }

        result.addAll(budgetAlerts(userId, today));
        return result;
    }

    private Computed billAlert(AlertKind kind, BillType type, BillAlertTotals totals, String key, boolean withLimitDate) {
        BigDecimal total = totals.getTotal().setScale(2, RoundingMode.HALF_UP);
        AlertResponse alert = new AlertResponse(
                key, kind, type, totals.getBillCount(), total,
                withLimitDate ? totals.getLastDueDate() : null,
                null, null, null, null, null, null, null);
        return new Computed(alert, totals.getBillCount() + "|" + total.toPlainString());
    }

    /** Estourado primeiro, depois os em 80%; dentro de cada grupo, por nome da categoria. */
    private List<Computed> budgetAlerts(Long userId, LocalDate today) {
        LocalDate firstDay = today.withDayOfMonth(1);
        List<BudgetVsActual> budgets = reportRepository.findBudgetVsActual(
                userId, today.getMonthValue(), today.getYear(), firstDay, firstDay.plusMonths(1));

        List<Computed> exceeded = new ArrayList<>();
        List<Computed> warning = new ArrayList<>();

        for (BudgetVsActual budget : budgets) {
            BigDecimal limit = budget.getBudgetedAmount();
            BigDecimal spent = budget.getSpentAmount();
            if (limit.signum() <= 0) {
                continue;
            }

            // Sem dividir: 80,00% já conta, 100,00% exato ainda não estourou.
            if (spent.compareTo(limit) > 0) {
                exceeded.add(budgetAlert(AlertKind.BUDGET_EXCEEDED, "BUDGET_EXCEEDED", budget, today));
            } else if (spent.compareTo(limit.multiply(WARNING_RATIO)) >= 0) {
                warning.add(budgetAlert(AlertKind.BUDGET_WARNING, "BUDGET_WARNING", budget, today));
            }
        }

        exceeded.addAll(warning);
        return exceeded;
    }

    /**
     * A faixa e o mês estão na chave, então a impressão digital é vazia: só mudar de faixa ou de mês
     * traz o alerta de volta, não o gasto maior dentro da mesma faixa.
     */
    private Computed budgetAlert(AlertKind kind, String prefix, BudgetVsActual budget, LocalDate today) {
        BigDecimal limit = budget.getBudgetedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal spent = budget.getSpentAmount().setScale(2, RoundingMode.HALF_UP);
        int percentUsed = spent.multiply(HUNDRED).divide(limit, 0, RoundingMode.DOWN).intValue();

        String key = String.format("%s:%d:%d-%02d", prefix, budget.getCategoryId(), today.getYear(), today.getMonthValue());

        AlertResponse alert = new AlertResponse(
                key, kind, null, null, null, null,
                budget.getCategoryId(), budget.getCategoryName(), spent, limit, percentUsed,
                today.getMonthValue(), today.getYear());
        return new Computed(alert, "");
    }

    private Map<BillType, BillAlertTotals> byType(List<BillAlertTotals> totals) {
        return totals.stream().collect(Collectors.toMap(BillAlertTotals::getType, t -> t));
    }
}
