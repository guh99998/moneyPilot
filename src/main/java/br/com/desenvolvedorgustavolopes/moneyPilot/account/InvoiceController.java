package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Faturas", description = "Faturas dos cartões de crédito da pessoa autenticada, calculadas na hora.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;

    @Operation(summary = "Lista as faturas a pagar",
            description = "Faturas aberta, fechada e vencida, de todos os cartões com fechamento e vencimento, que ainda têm valor a pagar, da que vence primeiro à que vence depois. A fatura futura não entra. É a visão das faturas como contas a pagar: use /accounts/{id}/invoices para o histórico completo de um cartão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Faturas a pagar"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/payable")
    @ResponseStatus(HttpStatus.OK)
    public List<PayableInvoiceResponse> listPayable() {
        return service.listPayable();
    }
}
