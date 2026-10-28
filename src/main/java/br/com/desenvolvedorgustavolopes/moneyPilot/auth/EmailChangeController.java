package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Troca de e-mail", description = "Troca do e-mail da conta com confirmação por link.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmailChangeController {

    private final EmailChangeService service;

    @Operation(summary = "Pede a troca de e-mail", description = "Envia um link de confirmação para o novo endereço. Exige a senha atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pedido registrado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/profile/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestEmailChange(@Valid @RequestBody EmailChangeRequest request) {
        service.request(request);
    }

    // Fica sob /auth porque o cookie refresh_token só é enviado a essa árvore de rotas.
    // Pública: o link do e-mail funciona sem sessão aberta.
    @Operation(summary = "Confirma a troca de e-mail", description = "Confirma o token recebido por e-mail e troca o endereço.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "E-mail trocado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/auth/email/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request,
                                   @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        service.confirm(request.token(), refreshToken);
    }
}
