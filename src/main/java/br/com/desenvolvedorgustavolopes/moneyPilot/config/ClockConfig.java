package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        ZoneId zoneId = ZoneId.of("America/Sao_Paulo");
        return Clock.system(zoneId);
    }
}
