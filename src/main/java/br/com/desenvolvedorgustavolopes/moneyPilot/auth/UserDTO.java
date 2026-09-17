package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

public record UserDTO(
        Long idUser,
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
