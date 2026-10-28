package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AlertsResponse(
        @Schema(description = "Quantidade de alertas ativos", example = "2") int count,
        @Schema(description = "Alertas ativos") List<AlertResponse> alerts
) {
    public AlertsResponse(List<AlertResponse> alerts) {
        this(alerts.size(), alerts);
    }
}
