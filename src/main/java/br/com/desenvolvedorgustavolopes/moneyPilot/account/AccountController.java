package br.com.desenvolvedorgustavolopes.moneyPilot.account;

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

@Tag(name = "Contas", description = "Contas bancárias, carteiras e cartões da pessoa autenticada.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;

    @Operation(summary = "Cria uma conta", description = "Cria uma conta com o saldo inicial informado (2 casas decimais, ponto decimal).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request) {
        return service.createAccount(request);
    }

    @Operation(summary = "Lista as contas", description = "Lista paginada: use page, size e sort (ex.: sort=name,asc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de contas"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        return service.getAllAccounts(pageable);
    }

    @Operation(summary = "Busca uma conta", description = "Devolve uma conta pelo id. Conta de outra pessoa responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta encontrada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public AccountResponse getAccountById(@PathVariable Long id) {
        return service.getAccountById(id);
    }

    @Operation(summary = "Atualiza uma conta", description = "Substitui nome, tipo e saldo inicial da conta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public AccountResponse updateAccountById(@PathVariable Long id, @RequestBody @Valid AccountRequest request) {
        return service.updateAccountById(id, request);
    }

    @Operation(summary = "Apaga uma conta", description = "Apaga a conta. Falha com 409 se ela já tem lançamentos ou contas a pagar ou receber.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conta apagada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccountById(@PathVariable Long id) {
        service.deleteAccountById(id);
    }

    @Operation(summary = "Consulta o saldo", description = "Saldo atual da conta: saldo inicial somado aos lançamentos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo da conta"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/balance")
    @ResponseStatus(HttpStatus.OK)
    public BalanceResponse getAccountBalance(@PathVariable Long id) {
        return service.getAccountBalance(id);
    }

}
