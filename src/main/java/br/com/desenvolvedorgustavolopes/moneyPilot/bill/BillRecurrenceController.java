package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Recorrências", description = "Regras que geram uma conta a pagar ou receber todo mês.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bill-recurrences")
public class BillRecurrenceController {

    private final BillRecurrenceService service;

    @Operation(summary = "Lista as recorrências", description = "Lista paginada: use page, size e sort (ex.: sort=name,asc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de recorrências"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<BillRecurrenceResponse> getAllBillRecurrences(Pageable pageable) {
        return service.getAllBillRecurrences(pageable);
    }

    @Operation(summary = "Busca uma recorrência", description = "Devolve a recorrência pelo id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recorrência encontrada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BillRecurrenceResponse getBillRecurrenceById(@PathVariable Long id) {
        return service.getBillRecurrenceById(id);
    }

    @Operation(summary = "Cria uma recorrência", description = "Cria a regra mensal. O dia do mês vai de 1 a 31 e a data final, se houver, não pode ser anterior à inicial.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recorrência criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public BillRecurrenceResponse createBillRecurrence(@Valid @RequestBody BillRecurrenceRequest request) {
        return service.createBillRecurrence(request);
    }

    @Operation(summary = "Atualiza uma recorrência", description = "Substitui os dados da regra. Contas já geradas não mudam.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recorrência atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BillRecurrenceResponse updateBillRecurrence(@PathVariable Long id, @Valid @RequestBody BillRecurrenceRequest request) {
        return service.updateBillRecurrence(id, request);
    }

    @Operation(summary = "Apaga uma recorrência", description = "Falha com 409 se a regra já gerou contas; nesse caso, desative.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recorrência apagada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBillRecurrence(@PathVariable Long id) {
        service.deleteBillRecurrence(id);
    }

    @Operation(summary = "Desativa uma recorrência", description = "A regra para de gerar contas novas e as já geradas ficam.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recorrência desativada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public BillRecurrenceResponse deactivateBillRecurrence(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
