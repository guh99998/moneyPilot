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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Contas a pagar e receber", description = "Contas com vencimento: avulsas, parceladas ou geradas por recorrência.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bills")
public class BillController {

    private final BillService service;

    @Operation(summary = "Lista as contas a pagar e receber", description = "Filtros opcionais por conta, categoria, tipo, status e vencimento. Use page e size para paginar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de contas"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<BillResponse> getAllBills(@RequestParam(required = false) Long accountId,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) BillType type,
                                          @RequestParam(required = false) BillStatusFilter status,
                                          @RequestParam(required = false) LocalDate dueDateFrom,
                                          @RequestParam(required = false) LocalDate dueDateTo,
                                          @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.getAllBills(accountId, categoryId, type, status, dueDateFrom, dueDateTo, pageable);
    }

    @Operation(summary = "Cria uma conta a pagar ou receber", description = "Cria uma conta em aberto com vencimento.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse createBill(@RequestBody @Valid BillRequest request) {
        return service.createBill(request);
    }

    @Operation(summary = "Busca uma conta a pagar ou receber", description = "Devolve a conta pelo id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta encontrada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse getBillById(@PathVariable Long id) {
        return service.getBillById(id);
    }

    @Operation(summary = "Atualiza uma conta a pagar ou receber", description = "Só contas em aberto podem mudar; as outras respondem 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse updateBill(@PathVariable Long id, @RequestBody @Valid BillRequest request) {
        return service.updateBill(id, request);
    }

    @Operation(summary = "Apaga uma conta a pagar ou receber", description = "Só contas em aberto podem ser apagadas; as outras respondem 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conta apagada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBill(@PathVariable Long id) {
        service.deleteBill(id);
    }

    @Operation(summary = "Cancela uma conta", description = "Marca uma conta em aberto como cancelada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta cancelada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse cancelBill(@PathVariable Long id) {
        return service.cancel(id);
    }

    @Operation(summary = "Quita uma conta", description = "Quita a conta numa conta bancária e gera o lançamento. A data de quitação não pode ser futura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta quitada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/settle")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse settleBill(@PathVariable Long id, @RequestBody @Valid SettleRequest request) {
        return service.settle(id, request);
    }

    @Operation(summary = "Desfaz a quitação", description = "Volta a conta para em aberto e apaga o lançamento gerado. Só vale para contas quitadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quitação desfeita"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/unsettle")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse unsettleBill(@PathVariable Long id) {
        return service.unsettle(id);
    }

    @Operation(summary = "Cria um parcelamento", description = "Divide o total em parcelas mensais. O total aceita 2 casas decimais e a parcela mínima é de 1 centavo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Parcelas criadas"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/installments")
    @ResponseStatus(HttpStatus.CREATED)
    public List<BillResponse> createInstallments(@RequestBody @Valid InstallmentPlanRequest request) {
        return service.createInstallmentPlan(request);
    }

    @Operation(summary = "Apaga um parcelamento", description = "Apaga todas as parcelas do grupo. Falha com 409 se alguma já foi quitada.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Parcelamento apagado"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/installments/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInstallmentGroup(@PathVariable UUID groupId) {
        service.deleteInstallmentGroup(groupId);
    }

    @Operation(summary = "Quita várias contas", description = "Quita as contas informadas de uma vez na mesma conta bancária.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contas quitadas"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/settle")
    @ResponseStatus(HttpStatus.OK)
    public List<BillResponse> bulkSettle(@RequestBody @Valid BulkSettleRequest request) {
        return service.bulkSettle(request);
    }

    @Operation(summary = "Gera as contas das recorrências", description = "Cria as contas do mês (aaaa-mm) a partir das recorrências ativas. Chamar de novo não duplica.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contas do mês"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @PostMapping("/materialize")
    @ResponseStatus(HttpStatus.OK)
    public List<BillResponse> materialize(@RequestParam YearMonth month) {
        return service.materializeFromRecurrences(month);
    }
}
