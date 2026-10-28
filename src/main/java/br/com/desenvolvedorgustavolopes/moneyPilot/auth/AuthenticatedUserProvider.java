package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.UnauthenticatedRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserProvider {

    private final UserRepository repository;

    /** Como {@link #getCurrentUserId()}, mas para rotas públicas de login opcional: sem sessão válida, vazio. */
    public Optional<Long> findCurrentUserId() {
        try {
            return Optional.of(getCurrentUserId());
        } catch (UnauthenticatedRequestException e) {
            return Optional.empty();
        }
    }

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new UnauthenticatedRequestException();
        }

        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new UnauthenticatedRequestException();
        }

        if (!repository.existsById(userId)) {
            throw new UnauthenticatedRequestException();
        }

        return userId;
    }
}
