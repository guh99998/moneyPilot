package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import br.com.desenvolvedorgustavolopes.moneyPilot.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, MailProperties.class})
public class PropertiesConfig {
}
