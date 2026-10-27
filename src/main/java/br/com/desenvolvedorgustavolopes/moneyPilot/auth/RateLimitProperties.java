package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        int loginPerIp,
        int loginFailuresPerEmail,
        Duration loginWindow,
        int registerPerIp,
        Duration registerWindow,
        int passwordChangeFailures,
        Duration passwordChangeWindow,
        int emailChangeRequests,
        Duration emailChangeWindow
) {
}
