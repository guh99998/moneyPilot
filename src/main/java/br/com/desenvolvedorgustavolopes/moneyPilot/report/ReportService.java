package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidForecastPeriodException;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRecurrence;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillRecurrenceRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.bill.BillType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final AuthenticatedUserProvider userProvider;
    private final ReportRepository reportRepository;
    private final BillRecurrenceRepository billRecurrenceRepository;
    private final Clock clock;

    private static final Set<Integer> FORECAST_PERIODS = Set.of(30, 60, 90);
    private static final int BUCKET_DAYS = 7;

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

    public CashFlowForecastResponse getCashFlowForecast(Integer days) {
        if (!FORECAST_PERIODS.contains(days)) {
            throw new InvalidForecastPeriodException(days);
        }

        Long userId = userProvider.getCurrentUserId();

        LocalDate from = LocalDate.now(clock);
        LocalDate to = from.plusDays(days - 1);

        BigDecimal currentBalance = reportRepository.getTotalBalance(userId);
        List<BillDueTotals> dueTotals = reportRepository.findOpenBillTotalsByDueDate(userId, to);

        int bucketCount = (days + BUCKET_DAYS - 1) / BUCKET_DAYS;
        BigDecimal[] payable = new BigDecimal[bucketCount];
        BigDecimal[] receivable = new BigDecimal[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            payable[i] = BigDecimal.ZERO;
            receivable[i] = BigDecimal.ZERO;
        }

        long overdueCount = 0;
        BigDecimal overduePayable = BigDecimal.ZERO;
        BigDecimal overdueReceivable = BigDecimal.ZERO;

        for (BillDueTotals totals : dueTotals) {
            int index;
            if (totals.getDueDate().isBefore(from)) {
                // Overdue bills stay in the projection: they land whole in the first bucket.
                index = 0;
                overdueCount += totals.getBillCount();
                overduePayable = overduePayable.add(totals.getTotalPayable());
                overdueReceivable = overdueReceivable.add(totals.getTotalReceivable());
            } else {
                index = (int) (ChronoUnit.DAYS.between(from, totals.getDueDate()) / BUCKET_DAYS);
            }
            payable[index] = payable[index].add(totals.getTotalPayable());
            receivable[index] = receivable[index].add(totals.getTotalReceivable());
        }

        List<ForecastBucketResponse> buckets = new ArrayList<>(bucketCount);
        BigDecimal runningBalance = currentBalance;
        for (int i = 0; i < bucketCount; i++) {
            LocalDate start = from.plusDays((long) i * BUCKET_DAYS);
            LocalDate end = start.plusDays(BUCKET_DAYS - 1);
            if (end.isAfter(to)) {
                end = to;
            }

            BigDecimal net = receivable[i].subtract(payable[i]);
            runningBalance = runningBalance.add(net);

            buckets.add(new ForecastBucketResponse(
                    start,
                    end,
                    (int) ChronoUnit.DAYS.between(start, end) + 1,
                    payable[i],
                    receivable[i],
                    net,
                    runningBalance
            ));
        }

        return new CashFlowForecastResponse(
                currentBalance,
                from,
                to,
                new OverdueSummaryResponse(overdueCount, overduePayable, overdueReceivable),
                buckets
        );
    }

    /**
     * Previsão mês a mês, do mês atual em diante. Vencidos entram no primeiro mês; recorrências
     * ainda não materializadas entram como previstas (nada é gravado), para os meses mais à frente
     * não ficarem sem as contas fixas.
     */
    public MonthlyForecastResponse getMonthlyForecast(Integer months) {
        Long userId = userProvider.getCurrentUserId();

        LocalDate today = LocalDate.now(clock);
        YearMonth firstMonth = YearMonth.from(today);
        LocalDate from = firstMonth.atDay(1);
        LocalDate to = firstMonth.plusMonths(months - 1).atEndOfMonth();

        BigDecimal currentBalance = reportRepository.getTotalBalance(userId);

        BigDecimal[] payable = new BigDecimal[months];
        BigDecimal[] receivable = new BigDecimal[months];
        long[] billCount = new long[months];
        for (int i = 0; i < months; i++) {
            payable[i] = BigDecimal.ZERO;
            receivable[i] = BigDecimal.ZERO;
        }

        long overdueCount = 0;
        BigDecimal overduePayable = BigDecimal.ZERO;
        BigDecimal overdueReceivable = BigDecimal.ZERO;

        for (BillDueTotals totals : reportRepository.findOpenBillTotalsByDueDate(userId, to)) {
            int index = 0;
            if (totals.getDueDate().isBefore(today)) {
                overdueCount += totals.getBillCount();
                overduePayable = overduePayable.add(totals.getTotalPayable());
                overdueReceivable = overdueReceivable.add(totals.getTotalReceivable());
            }
            if (!totals.getDueDate().isBefore(from)) {
                index = (int) ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(totals.getDueDate()));
            }
            payable[index] = payable[index].add(totals.getTotalPayable());
            receivable[index] = receivable[index].add(totals.getTotalReceivable());
            billCount[index] += totals.getBillCount();
        }

        Set<String> existing = new HashSet<>();
        for (RecurrenceBillKey key : reportRepository.findRecurrenceBillKeys(userId, from, to)) {
            existing.add(key.getRecurrenceId() + "|" + key.getDueDate());
        }

        for (BillRecurrence recurrence : billRecurrenceRepository.findAllByUserIdAndActiveTrue(userId)) {
            for (int i = 0; i < months; i++) {
                YearMonth month = firstMonth.plusMonths(i);
                LocalDate dueDate = month.atDay(Math.min(month.lengthOfMonth(), recurrence.getDayOfMonth()));

                if (dueDate.isBefore(today)
                        || dueDate.isBefore(recurrence.getStartDate())
                        || (recurrence.getEndDate() != null && dueDate.isAfter(recurrence.getEndDate()))
                        || existing.contains(recurrence.getId() + "|" + dueDate)) {
                    continue;
                }

                if (recurrence.getType() == BillType.PAYABLE) {
                    payable[i] = payable[i].add(recurrence.getAmount());
                } else {
                    receivable[i] = receivable[i].add(recurrence.getAmount());
                }
                billCount[i]++;
            }
        }

        List<MonthlyForecastBucketResponse> buckets = new ArrayList<>(months);
        BigDecimal runningBalance = currentBalance;
        for (int i = 0; i < months; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            BigDecimal net = receivable[i].subtract(payable[i]);
            runningBalance = runningBalance.add(net);

            buckets.add(new MonthlyForecastBucketResponse(
                    month.getMonthValue(),
                    month.getYear(),
                    month.atDay(1),
                    month.atEndOfMonth(),
                    billCount[i],
                    payable[i],
                    receivable[i],
                    net,
                    runningBalance
            ));
        }

        return new MonthlyForecastResponse(
                currentBalance,
                from,
                to,
                new OverdueSummaryResponse(overdueCount, overduePayable, overdueReceivable),
                buckets
        );
    }

    public BillsSummaryResponse getBillsSummary() {
        Long userId = userProvider.getCurrentUserId();

        LocalDate today = LocalDate.now(clock);
        LocalDate monthStart = today.withDayOfMonth(1);

        // "Baixado no mês" segue a data do pagamento (a do lançamento gerado), não a do clique.
        return new BillsSummaryResponse(
                reportRepository.findBillsSummary(userId, today, monthStart, monthStart.plusMonths(1))
        );
    }
}
