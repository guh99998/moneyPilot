package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Alertas", description = "Avisos de contas vencendo e orçamentos estourados.")
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;

    @Operation(summary = "Lista os alertas", description = "Alertas ativos agora, os que a pessoa ainda não dispensou.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alertas ativos"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping
    public AlertsResponse listAlerts() {
        return service.listAlerts();
    }

    @Operation(summary = "Dispensa um alerta", description = "Esconde o alerta identificado pela chave informada.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alerta dispensado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@Valid @RequestBody DismissAlertRequest request) {
        service.dismiss(request.key());
    }
}
