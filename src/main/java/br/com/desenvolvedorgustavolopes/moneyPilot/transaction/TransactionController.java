package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

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

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Lançamentos", description = "Receitas e despesas lançadas nas contas, e transferências entre contas.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    @Operation(summary = "Lança uma receita ou despesa", description = "Cria o lançamento numa conta e categoria da pessoa. O tipo da categoria deve combinar com o tipo do lançamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançamento criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody @Valid TransactionRequest request) {
        return service.createTransaction(request);
    }

    @Operation(summary = "Lista os lançamentos", description = "Filtros opcionais por conta, categoria, período (from e to, aaaa-mm-dd) e tipo. Mais recentes primeiro. Use page e size para paginar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de lançamentos"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<TransactionResponse> getAllTransactions(@RequestParam(required = false) Long accountId, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) TransactionFilterType type, @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.getAllTransactions(accountId, categoryId, from, to, type, pageable);
    }

    @Operation(summary = "Busca um lançamento", description = "Devolve um lançamento pelo id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lançamento encontrado"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public TransactionResponse getTransactionById(@PathVariable Long id) {
        return service.getTransactionById(id);
    }

    @Operation(summary = "Atualiza um lançamento", description = "Substitui os dados do lançamento. Falha com 409 se ele for parte de uma transferência.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lançamento atualizado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public TransactionResponse updateTransaction(@PathVariable Long id, @RequestBody @Valid TransactionRequest request) {
        return service.updateTransaction(id, request);
    }

    @Operation(summary = "Apaga um lançamento", description = "Apaga o lançamento. Falha com 409 se for parte de uma transferência ou estiver ligado a uma conta paga.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lançamento apagado"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(@PathVariable Long id) {
        service.deleteTransaction(id);
    }

    @Operation(summary = "Transfere entre contas", description = "Cria a saída na conta de origem e a entrada na de destino. As contas devem ser diferentes.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Os dois lançamentos da transferência"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransactionResponse> createTransfer(@RequestBody @Valid TransferRequest request) {
        return service.createTransfer(request);
    }
}
