package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.EmailAlreadyRegistredException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserDTO createUser(RegisterRequest request) {
        if(existByEmail(request.email())) {
            throw new EmailAlreadyRegistredException();
        }

        User newUser = new User();

        newUser.setEmail(request.email());
        newUser.setPasswordHash(passwordEncoder.encode(request.password()));
        newUser.setName(request.name());
        newUser.setCreatedAt(Instant.now());

        return new UserDTO(repository.save(newUser));
    }

    private boolean existByEmail(String email) {
        return repository.existsByEmail(email);
    }
}
