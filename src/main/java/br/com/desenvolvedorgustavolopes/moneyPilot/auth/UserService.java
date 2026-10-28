package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter attemptLimiter;
    private final RateLimitProperties rateLimit;
    private final EmailVerificationService verificationService;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    /**
     * O resultado é o mesmo para quem chama, exista ou não a conta, para não revelar quais e-mails estão cadastrados.
     */
    @Transactional
    public void createUser(RegisterRequest request, String clientIp) {
        String ipKey = "register-ip:" + clientIp;
        attemptLimiter.check(ipKey, rateLimit.registerPerIp());
        attemptLimiter.record(ipKey, rateLimit.registerWindow());

        // Antes de procurar a conta, com os dados do formulário: a resposta não depende de a conta existir.
        passwordPolicy.checkPersonal("password", request.password(), request.email(), request.name());

        User user = repository.findUserByEmail(request.email()).orElse(null);

        if (user != null && user.getEmailVerifiedAt() != null) {
            verificationService.notifyRegisterAttempt(user);
            return;
        }

        // Conta que nunca foi verificada nunca entrou: é reaproveitada com os dados do novo cadastro.
        if (user == null) {
            user = new User();
            user.setEmail(request.email());
            user.setCreatedAt(clock.instant());
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        repository.saveAndFlush(user);

        // Se o envio falhar, a exceção desfaz a transação: nenhuma conta fica gravada.
        verificationService.sendLink(user);
    }
}
