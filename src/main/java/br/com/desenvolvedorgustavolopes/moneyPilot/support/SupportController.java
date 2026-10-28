package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.UnauthenticatedRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// Pública com login opcional: quem não consegue entrar também precisa falar com o suporte.
@Tag(name = "Suporte", description = "Contato com o suporte do moneyPilot.")
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService service;
    private final AuthenticatedUserProvider authenticatedUser;

    @Operation(summary = "Fala com o suporte", description = "Envia uma mensagem para o suporte. Vale sem login; com login, a resposta vai para o e-mail da conta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado recebido e gravado, com a referência; vale mesmo que o e-mail ao suporte falhe"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/tickets")
    public SupportTicketResponse send(@Valid @RequestBody SupportTicketRequest request, HttpServletRequest httpRequest) {
        // Token enviado mas recusado (expirado): 401 faz o cliente renovar e reenviar, em vez de tratar como anônimo.
        String authorization = httpRequest.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ") && authenticatedUser.findCurrentUserId().isEmpty()) {
            throw new UnauthenticatedRequestException();
        }
        return service.send(request, httpRequest.getRemoteAddr());
    }
}
