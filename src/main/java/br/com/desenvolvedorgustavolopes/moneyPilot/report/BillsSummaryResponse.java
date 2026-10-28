package br.com.desenvolvedorgustavolopes.moneyPilot.report;

import io.swagger.v3.oas.annotations.media.Schema;

public record BillsSummaryResponse(
        @Schema(description = "Resumo das contas a pagar") BillTypeSummaryResponse payable,
        @Schema(description = "Resumo das contas a receber") BillTypeSummaryResponse receivable,
        @Schema(description = "Contas quitadas neste mês") SettledSummaryResponse settledThisMonth
) {
    public BillsSummaryResponse(BillsSummaryTotals totals) {
        this(
                new BillTypeSummaryResponse(
                        totals.getPayableCount(),
                        totals.getPayableTotal(),
                        totals.getPayableOverdueCount(),
                        totals.getPayableOverdueTotal()
                ),
                new BillTypeSummaryResponse(
                        totals.getReceivableCount(),
                        totals.getReceivableTotal(),
                        totals.getReceivableOverdueCount(),
                        totals.getReceivableOverdueTotal()
                ),
                new SettledSummaryResponse(
                        totals.getSettledCount(),
                        totals.getSettledPayable(),
                        totals.getSettledReceivable()
                )
        );
    }
}
