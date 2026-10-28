package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Relatórios", description = "Resumos e previsões calculados a partir dos lançamentos e contas a pagar ou receber.")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    @Operation(summary = "Resumo do mês", description = "Receitas, despesas e saldo do mês.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo do mês"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("/monthly-summary")
    @ResponseStatus(HttpStatus.OK)
    public MonthlySummaryResponse getMonthlySummary(@RequestParam @Min(1) @Max(12) Integer month, @RequestParam Integer year) {
        return service.getMonthlySummary(month, year);
    }

    @Operation(summary = "Gasto por categoria", description = "Total gasto em cada categoria no mês.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gasto por categoria"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("/spending-by-category")
    @ResponseStatus(HttpStatus.OK)
    public List<CategorySpendingResponse> getSpendingByCategory(@RequestParam @Min(1) @Max(12) Integer month, @RequestParam Integer year) {
        return service.getSpendingByCategory(month, year);
    }

    @Operation(summary = "Orçado contra realizado", description = "Compara o limite de cada orçamento com o gasto real no mês.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçado e realizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("/budget-vs-actual")
    @ResponseStatus(HttpStatus.OK)
    public List<BudgetVsActualResponse> getBudgetVsActual(@RequestParam @Min(1) @Max(12) Integer month, @RequestParam Integer year) {
        return service.getBudgetVsActual(month, year);
    }

    @Operation(summary = "Previsão de fluxo de caixa", description = "Saldo projetado dia a dia nos próximos dias, com as contas a pagar e receber.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Previsão do fluxo de caixa"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("/cash-flow-forecast")
    @ResponseStatus(HttpStatus.OK)
    public CashFlowForecastResponse getCashFlowForecast(@RequestParam(defaultValue = "30") Integer days) {
        return service.getCashFlowForecast(days);
    }

    @Operation(summary = "Previsão mensal", description = "Previsão de receitas, despesas e saldo para os próximos meses (1 a 12).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Previsão mensal"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("/monthly-forecast")
    @ResponseStatus(HttpStatus.OK)
    public MonthlyForecastResponse getMonthlyForecast(@RequestParam(defaultValue = "6") @Min(1) @Max(12) Integer months) {
        return service.getMonthlyForecast(months);
    }

    @Operation(summary = "Resumo das contas a pagar e receber", description = "Totais em aberto, vencidos e a vencer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo das contas"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("/bills-summary")
    @ResponseStatus(HttpStatus.OK)
    public BillsSummaryResponse getBillsSummary() {
        return service.getBillsSummary();
    }
}
