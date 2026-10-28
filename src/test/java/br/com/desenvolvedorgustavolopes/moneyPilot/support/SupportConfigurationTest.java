package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.PropertiesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

// Em prod, sem o endereço do suporte o contexto não sobe.
class SupportConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues("app.public-url=https://app.exemplo.com");

    @Test
    void withoutTheSupportEmail_failsToStart() {
        runner.withPropertyValues("app.support.email=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void withTheSupportEmail_startsAndReadsIt() {
        runner.withPropertyValues("app.support.email=suporte@exemplo.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SupportProperties.class).email()).isEqualTo("suporte@exemplo.com");
                });
    }
}
