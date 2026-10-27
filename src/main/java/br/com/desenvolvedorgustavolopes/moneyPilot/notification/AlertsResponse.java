package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import java.util.List;

public record AlertsResponse(
        int count,
        List<AlertResponse> alerts
) {
    public AlertsResponse(List<AlertResponse> alerts) {
        this(alerts.size(), alerts);
    }
}
