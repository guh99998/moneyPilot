package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BillRecurrenceResponse(
        @Schema(description = "Identificador", example = "1") Long id,
        @Schema(description = "Identificador da pessoa dona do registro", example = "1") Long userId,
        @Schema(description = "Identificador da conta", example = "1") Long accountId,
        @Schema(description = "Identificador da categoria", example = "3") Long categoryId,
        @Schema(description = "Descrição livre", example = "Compras da semana") String description,
        @Schema(description = "Valor em reais, número com 2 casas decimais e ponto decimal.", example = "50.00") BigDecimal amount,
        @Schema(description = "A pagar ou a receber", enumAsRef = true) BillType type,
        @Schema(description = "Dia do mês do vencimento, de 1 a 31", example = "10") Integer dayOfMonth,
        @Schema(description = "Início, no formato aaaa-mm-dd", example = "2026-11-01") LocalDate startDate,
        @Schema(description = "Fim, no formato aaaa-mm-dd; vazio se não termina", example = "2027-10-31") LocalDate endDate,
        @Schema(description = "Se a recorrência ainda gera contas", example = "true") Boolean active,
        @Schema(description = "Criado em (UTC)", example = "2026-10-28T12:00:00Z") Instant createdAt,
        @Schema(description = "Atualizado em (UTC)", example = "2026-10-28T12:00:00Z") Instant updatedAt,
        @Schema(description = "Nome da conta", example = "Carteira") String accountName,
        @Schema(description = "Nome da categoria", example = "Mercado") String categoryName
) {
    public BillRecurrenceResponse(BillRecurrence billRecurrence, String accountName, String categoryName) {
        this(
                billRecurrence.getId(),
                billRecurrence.getUserId(),
                billRecurrence.getAccountId(),
                billRecurrence.getCategoryId(),
                billRecurrence.getDescription(),
                billRecurrence.getAmount(),
                billRecurrence.getType(),
                billRecurrence.getDayOfMonth(),
                billRecurrence.getStartDate(),
                billRecurrence.getEndDate(),
                billRecurrence.getActive(),
                billRecurrence.getCreatedAt(),
                billRecurrence.getUpdatedAt(),
                accountName,
                categoryName
        );
    }
}
