package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final AuthenticatedUserProvider userProvider;
    private final ReportRepository reportRepository;

    private LocalDate firstDayOf(Integer month, Integer year) {
        return LocalDate.of(year, month, 1);
    }

    public MonthlySummaryResponse getMonthlySummary(Integer month, Integer year) {
        Long userId = userProvider.getCurrentUserId();

        LocalDate from = this.firstDayOf(month, year);
        LocalDate nextMonth = from.plusMonths(1);

        MonthlyTotals totals = reportRepository.findMonthlyTotals(userId, from, nextMonth);

        return new MonthlySummaryResponse(
                totals.getTotalIncome(),
                totals.getTotalExpense(),
                totals.getTotalIncome().subtract(totals.getTotalExpense())
        );
    }

    public List<CategorySpendingResponse> getSpendingByCategory(Integer month, Integer year) {
        Long userId = userProvider.getCurrentUserId();

        LocalDate from = this.firstDayOf(month, year);
        LocalDate nextMonth = from.plusMonths(1);

        return reportRepository.findSpendingByCategory(userId, from, nextMonth).stream()
                .map(CategorySpendingResponse::new)
                .toList();
    }

    public List<BudgetVsActualResponse> getBudgetVsActual(Integer month, Integer year) {
        Long userId = userProvider.getCurrentUserId();

        LocalDate from = this.firstDayOf(month, year);
        LocalDate nextMonth = from.plusMonths(1);

        return reportRepository.findBudgetVsActual(userId, month, year, from, nextMonth).stream()
                .map(BudgetVsActualResponse::new)
                .toList();
    }
}
