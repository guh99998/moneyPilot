package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository repository;

    public User createUser(User user) {
        User newUser = new User();

        newUser.setEmail(user.getEmail());
        newUser.setPasswordHash(user.getPasswordHash());
        newUser.setName(user.getName());
        newUser.setCreatedAt(user.getCreatedAt());

        return repository.save(newUser);
    }

    public boolean existByEmail(String email) {
        return repository.existsByEmail(email);
    }

    public Optional<UserDTO> getUserByEmail(String email) {
        return Optional.of(repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(("Can't find user"))));
    }
}
