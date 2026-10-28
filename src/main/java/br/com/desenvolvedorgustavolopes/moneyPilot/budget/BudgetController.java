package br.com.desenvolvedorgustavolopes.moneyPilot.budget;

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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orçamentos", description = "Limite mensal de gasto por categoria.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService service;

    @Operation(summary = "Lista os orçamentos", description = "Filtros opcionais por categoria, mês e ano. Mais recentes primeiro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de orçamentos"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<BudgetResponse> getAllBudgets(@RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Integer month,
                                              @RequestParam(required = false) Integer year,
                                              @PageableDefault(size = 20, sort = {"year", "month"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.getAllBudgets(categoryId, month, year, pageable);
    }

    @Operation(summary = "Cria um orçamento", description = "Define o limite de uma categoria de despesa num mês. Só um orçamento por categoria e mês (409 se repetir).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orçamento criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse createBudget(@RequestBody @Valid BudgetRequest request) {
        return service.createBudget(request);
    }

    @Operation(summary = "Busca um orçamento", description = "Devolve um orçamento pelo id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamento encontrado"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BudgetResponse getBudgetById(@PathVariable Long id) {
        return service.getBudgetById(id);
    }

    @Operation(summary = "Atualiza um orçamento", description = "Substitui categoria, limite, mês e ano.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamento atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BudgetResponse updateBudget(@PathVariable Long id, @RequestBody @Valid BudgetRequest request) {
        return service.updateBudget(id, request);
    }

    @Operation(summary = "Apaga um orçamento", description = "Apaga o orçamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Orçamento apagado"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBudget(@PathVariable Long id) {
        service.deleteBudget(id);
    }
}
