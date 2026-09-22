package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

public record UserDTO(
        Long id,
        String email,
        String name
) {
    public UserDTO(User user) {
        this(
                user.getId(),
                user.getEmail(),
                user.getName()
        );
    }
}
