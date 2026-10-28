package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// Públicas: quem ainda não verificou a conta não consegue abrir sessão.
@Tag(name = "Verificação de conta", description = "Confirmação do e-mail depois do cadastro.")
@RestController
@RequestMapping("/api/v1/auth/verification")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService service;

    @Operation(summary = "Confirma o e-mail", description = "Confirma o token recebido por e-mail e libera o login.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "E-mail confirmado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody ConfirmVerificationRequest request) {
        service.confirm(request.token());
    }

    @Operation(summary = "Reenvia a verificação", description = "Reenvia o e-mail de verificação. Responde 202 mesmo se o e-mail não tiver conta, para não revelar quem está cadastrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Pedido aceito"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest httpRequest) {
        service.resend(request.email(), httpRequest.getRemoteAddr());
    }
}
