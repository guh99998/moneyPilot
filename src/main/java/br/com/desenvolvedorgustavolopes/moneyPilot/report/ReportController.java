package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    @GetMapping("/monthly-summary")
    @ResponseStatus(HttpStatus.OK)
    public MonthlySummaryResponse getMonthlySummary(@RequestParam @Min(1) @Max(12) Integer month, @RequestParam Integer year) {
        return service.getMonthlySummary(month, year);
    }

    @GetMapping("/spending-by-category")
    @ResponseStatus(HttpStatus.OK)
    public List<CategorySpendingResponse> getSpendingByCategory(@RequestParam @Min(1) @Max(12) Integer month, @RequestParam Integer year) {
        return service.getSpendingByCategory(month, year);
    }

    @GetMapping("/budget-vs-actual")
    @ResponseStatus(HttpStatus.OK)
    public List<BudgetVsActualResponse> getBudgetVsActual(@RequestParam @Min(1) @Max(12) Integer month, @RequestParam Integer year) {
        return service.getBudgetVsActual(month, year);
    }
}
