package br.com.desenvolvedorgustavolopes.moneyPilot.report;

public record BillsSummaryResponse(
        BillTypeSummaryResponse payable,
        BillTypeSummaryResponse receivable,
        SettledSummaryResponse settledThisMonth
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
