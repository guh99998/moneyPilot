package br.com.desenvolvedorgustavolopes.moneyPilot.mail;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.AppProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.config.PropertiesConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

// CA2: em prod, sem a configuração de envio o contexto não sobe. O contexto aqui não tem RestClient.Builder
// (como o app real), para o sender não depender de um bean que não existe.
class MailConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, ResendEmailSender.class)
            .withPropertyValues("spring.profiles.active=prod", "app.support.email=suporte@exemplo.com");

    @Test
    void prodWithoutMailKey_failsToStartWithClearMessage() {
        runner.withPropertyValues("app.public-url=https://app.exemplo.com",
                        "app.mail.from-address=nao-responda@exemplo.com",
                        "app.mail.resend-api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("RESEND_API_KEY");
                });
    }

    @Test
    void prodWithoutMailFrom_failsToStartWithClearMessage() {
        runner.withPropertyValues("app.public-url=https://app.exemplo.com",
                        "app.mail.resend-api-key=re_key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("MAIL_FROM_ADDRESS");
                });
    }

    @Test
    void prodWithoutPublicUrl_failsToStart() {
        runner.withPropertyValues("app.public-url=",
                        "app.mail.from-address=nao-responda@exemplo.com",
                        "app.mail.resend-api-key=re_key")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void prodFullyConfigured_startsAndTrimsTheTrailingSlash() {
        runner.withPropertyValues("app.public-url=https://app.exemplo.com/",
                        "app.mail.from-address=nao-responda@exemplo.com",
                        "app.mail.resend-api-key=re_key")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AppProperties.class).publicUrl()).isEqualTo("https://app.exemplo.com");
                });
    }
}
