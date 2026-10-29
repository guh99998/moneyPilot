package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import br.com.desenvolvedorgustavolopes.moneyPilot.mail.MailProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.support.SupportProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AppProperties.class, MailProperties.class, GoogleProperties.class, SupportProperties.class, AdminProperties.class, PixProperties.class})
public class PropertiesConfig {
}
