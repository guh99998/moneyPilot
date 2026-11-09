package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import br.com.desenvolvedorgustavolopes.moneyPilot.transaction.TransactionResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Compras recorrentes", description = "Cobranças mensais no cartão de crédito (assinaturas) que viram despesa sozinhas na data.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recurring-purchases")
public class RecurringPurchaseController {

    private final RecurringPurchaseService service;

    @Operation(summary = "Lista as compras recorrentes", description = "Lista paginada: use page, size e sort. Com accountId, só as do cartão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de compras recorrentes"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<RecurringPurchaseResponse> getAll(@RequestParam(required = false) Long accountId, Pageable pageable) {
        return service.getAll(accountId, pageable);
    }

    @Operation(summary = "Busca uma compra recorrente", description = "Devolve a compra recorrente pelo id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compra recorrente encontrada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public RecurringPurchaseResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @Operation(summary = "Cria uma compra recorrente", description = "Cria a regra mensal no cartão. A conta precisa ser um cartão de crédito com fechamento e vencimento (senão 400 no campo accountId) e a categoria precisa ser de despesa. As cobranças viram despesa sozinhas, na data, quando a rota /materialize roda.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compra recorrente criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringPurchaseResponse create(@Valid @RequestBody RecurringPurchaseRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Atualiza uma compra recorrente", description = "Substitui os dados da regra. Vale para as cobranças ainda não geradas; as já geradas não mudam.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compra recorrente atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public RecurringPurchaseResponse update(@PathVariable Long id, @Valid @RequestBody RecurringPurchaseRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Apaga uma compra recorrente", description = "Remove a regra e mantém as cobranças já geradas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Compra recorrente apagada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @Operation(summary = "Desativa uma compra recorrente", description = "A regra para de gerar e de projetar cobranças. As já geradas ficam.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compra recorrente desativada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public RecurringPurchaseResponse deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }

    @Operation(summary = "Gera as cobranças vencidas", description = "Cria como despesa no cartão cada cobrança já vencida (até hoje) das compras recorrentes ativas. Idempotente: chamar de novo não duplica, e uma cobrança apagada não é recriada. Devolve só as despesas criadas agora.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Despesas criadas nesta chamada (vazio se não havia nada a gerar)"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @PostMapping("/materialize")
    @ResponseStatus(HttpStatus.OK)
    public List<TransactionResponse> materialize() {
        return service.materialize();
    }
}
