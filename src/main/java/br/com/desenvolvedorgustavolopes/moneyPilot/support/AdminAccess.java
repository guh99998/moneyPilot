package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.UserRepository;
import br.com.desenvolvedorgustavolopes.moneyPilot.config.AdminProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.AdminOnlyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** O papel de administrador vem do e-mail atual da conta estar em {@code ADMIN_EMAILS}; não há papel gravado. */
@Component
@RequiredArgsConstructor
public class AdminAccess {

    private final AuthenticatedUserProvider authenticatedUser;
    private final UserRepository userRepository;
    private final AdminProperties properties;

    public boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> properties.isAdminEmail(user.getEmail()))
                .orElse(false);
    }

    public boolean isAdminEmail(String email) {
        return properties.isAdminEmail(email);
    }

    /** Sem login lança 401 (via o provedor); logado sem ser administrador, 404. */
    public void requireAdmin() {
        if (!isAdmin(authenticatedUser.getCurrentUserId())) {
            throw new AdminOnlyException();
        }
    }
}
