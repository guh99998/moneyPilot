package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final AuthenticatedUserProvider userProvider;
    private final ReportRepository reportRepository;

    public MonthlySummaryResponse getMonthlySummary(Integer month, Integer year) {
        Long userId = userProvider.getCurrentUserId();

        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate nextMonth = from.plusMonths(1);

        MonthlyTotals totals = reportRepository.findMonthlyTotals(userId, from, nextMonth);

        return new MonthlySummaryResponse(
                totals.getTotalIncome(),
                totals.getTotalExpense(),
                totals.getTotalIncome().subtract(totals.getTotalExpense())
        );
    }
}
