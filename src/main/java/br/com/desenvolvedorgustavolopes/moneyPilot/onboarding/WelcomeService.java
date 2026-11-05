package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.User;
import br.com.desenvolvedorgustavolopes.moneyPilot.config.AppProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailMessage;
import br.com.desenvolvedorgustavolopes.moneyPilot.mail.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Boas-vindas de uma conta nova e verificada (spec 013). Acontece uma vez por conta. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeService {

    private final OnboardingRepository onboardingRepository;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final Clock clock;

    /**
     * Registra as boas-vindas e envia o e-mail, se a conta ainda não passou por elas. Conta antiga nunca chega
     * aqui. Falha no envio vai só para o log: não desfaz a verificação nem o login, e o e-mail não é reenviado
     * porque a linha já foi gravada.
     */
    @Transactional
    public void welcome(User user) {
        if (onboardingRepository.insertIfAbsent(user.getId(), clock.instant()) == 0) {
            return;
        }

        try {
            emailSender.send(welcomeMessage(user));
        } catch (RuntimeException e) {
            log.error("Could not send the welcome e-mail: {}", e.getClass().getSimpleName());
        }
    }

    private EmailMessage welcomeMessage(User user) {
        String text = """
                Olá, %s!

                Sua conta no moneyPilot está pronta. Para o app ficar útil logo de cara, sugerimos três passos:

                1. Crie sua primeira conta bancária.
                2. Lance sua primeira despesa ou receita.
                3. Crie um orçamento.

                Abrir o moneyPilot: %s/app/

                O moneyPilot é gratuito e seus dados são só seus. Se precisar de ajuda, use "Falar com o suporte" dentro do app.
                """.formatted(user.getName(), appProperties.publicUrl());

        return new EmailMessage(user.getEmail(), "Bem-vindo ao moneyPilot", text);
    }
}
