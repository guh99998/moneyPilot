package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Primeiros passos", description = "Pop-up de primeiro contato: os primeiros passos para o app ficar útil, só para conta nova, com a preferência \"Mostrar ao iniciar\".")
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService service;

    @Operation(summary = "Busca o estado do pop-up de primeiros passos", description = "Devolve sempre os três passos. visible diz se o pop-up abre sozinho no login (elegível e com showOnStart marcado); eligible diz se a caixinha \"Mostrar ao iniciar\" tem efeito. Conta antiga, cartão antigo dispensado e passos concluídos respondem eligible=false e visible=false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado do pop-up"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public OnboardingResponse get() {
        return service.get();
    }

    @Operation(summary = "Guarda a preferência \"Mostrar ao iniciar\"", description = "Vale para a conta, em todos os aparelhos. Pode ser repetido sem efeito; conta antiga (sem registro das boas-vindas) não muda nada.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Preferência guardada"),
            @ApiResponse(responseCode = "400", description = "showOnStart ausente ou inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/preferences")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void savePreferences(@Valid @RequestBody OnboardingPreferencesRequest request) {
        service.savePreferences(request);
    }
}
