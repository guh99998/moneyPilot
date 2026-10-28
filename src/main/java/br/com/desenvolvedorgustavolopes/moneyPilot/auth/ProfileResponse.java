package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ProfileResponse(
        @Schema(description = "Nome exibido", example = "Ana Souza") String name,
        @Schema(description = "E-mail", example = "ana@exemplo.com") String email,
        @Schema(description = "Criado em (UTC)", example = "2026-10-28T12:00:00Z") Instant createdAt,
        @Schema(description = "E-mail novo aguardando confirmação, se houver", example = "ana.nova@exemplo.com") String pendingEmail,
        @Schema(description = "Se a conta tem senha (conta só com Google não tem)", example = "true") boolean hasPassword,
        @Schema(description = "E-mail da conta Google vinculada, se houver", example = "ana@gmail.com") String googleEmail,
        @Schema(description = "Endereço da foto do Google, se houver e não estiver escondida", example = "https://lh3.googleusercontent.com/a/exemplo") String avatarUrl,
        @Schema(description = "Se a pessoa escondeu a foto do Google", example = "false") boolean avatarHidden,
        @Schema(description = "Se a conta é administradora do painel de chamados do suporte", example = "false") boolean admin
) {
    public ProfileResponse(User user, String pendingEmail, boolean admin) {
        this(
                user.getName(),
                user.getEmail(),
                user.getCreatedAt(),
                pendingEmail,
                user.hasPassword(),
                user.getGoogleEmail(),
                user.isAvatarHidden() ? null : user.getAvatarUrl(),
                user.isAvatarHidden(),
                admin
        );
    }
}
