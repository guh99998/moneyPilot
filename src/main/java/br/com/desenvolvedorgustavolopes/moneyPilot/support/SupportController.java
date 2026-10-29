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
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

// Pública com login opcional: quem não consegue entrar também precisa falar com o suporte.
@Tag(name = "Suporte", description = "Contato com o suporte do moneyPilot.")
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService service;
    private final MySupportTicketService myTickets;
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

    @Operation(summary = "Lista os meus chamados", description = "Os chamados abertos com a conta logada, mais recentes primeiro, 10 por página (parâmetro page, a partir de 0). Chamados enviados sem login não entram.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de chamados"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("/tickets")
    public Page<MySupportTicketSummaryResponse> listMine(@RequestParam(defaultValue = "0") int page) {
        return myTickets.list(page);
    }

    @Operation(summary = "Busca um dos meus chamados", description = "Devolve o chamado com a mensagem enviada. Chamado de outra conta responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado encontrado"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente ou de outra conta", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/tickets/{reference}")
    public MySupportTicketDetailResponse getMine(@PathVariable String reference) {
        return myTickets.get(reference);
    }
}
