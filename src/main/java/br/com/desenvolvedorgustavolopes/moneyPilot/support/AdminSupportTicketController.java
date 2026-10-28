package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Painel de chamados", description = "Chamados do suporte, só para administradores. Para os demais, as rotas respondem 404.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/support/tickets")
public class AdminSupportTicketController {

    private final AdminSupportTicketService service;

    @Operation(summary = "Lista os chamados do suporte", description = "Mais recentes primeiro, com filtro opcional por status. Use page e size para paginar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de chamados"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Quem não é administrador vê a rota como inexistente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("")
    public Page<SupportTicketSummaryResponse> list(@RequestParam(required = false) SupportTicketStatus status,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return service.list(status, pageable);
    }

    @Operation(summary = "Busca um chamado do suporte", description = "Devolve o chamado completo, com a mensagem.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado encontrado"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente, ou quem pede não é administrador", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public SupportTicketDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Muda o status de um chamado", description = "Aceita qualquer status, inclusive reabrir um chamado resolvido. O mesmo status que já tem é aceito, sem efeito.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chamado atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Chamado inexistente, ou quem pede não é administrador", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public SupportTicketDetailResponse updateStatus(@PathVariable Long id, @RequestBody @Valid UpdateSupportTicketStatusRequest request) {
        return service.updateStatus(id, request);
    }
}
