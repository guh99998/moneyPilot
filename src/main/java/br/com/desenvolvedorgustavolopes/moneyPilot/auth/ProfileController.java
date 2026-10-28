package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Perfil", description = "Dados e senha da pessoa autenticada.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;

    @Operation(summary = "Consulta o perfil", description = "Nome, e-mail, avatar e se a conta tem senha e Google ligados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("/profile")
    public ProfileResponse getProfile() {
        return service.getProfile();
    }

    @Operation(summary = "Atualiza o perfil", description = "Muda o nome exibido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @PutMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return service.updateName(request);
    }

    // Fica sob /auth porque o cookie refresh_token só é enviado a essa árvore de rotas.
    @Operation(summary = "Troca a senha", description = "Troca (ou cria, em conta só com Google) a senha. Encerra as outras sessões, inclusive as da API (só a sessão do navegador que fez a troca continua): entre de novo depois.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Senha trocada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        service.changePassword(request, refreshToken);
    }
}
