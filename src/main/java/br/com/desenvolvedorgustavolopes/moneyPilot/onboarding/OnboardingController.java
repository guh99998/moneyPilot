package br.com.desenvolvedorgustavolopes.moneyPilot.onboarding;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Primeiros passos", description = "Cartão de boas-vindas do painel: só aparece em conta nova, com os primeiros passos para o app ficar útil.")
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService service;

    @Operation(summary = "Busca o cartão de primeiros passos", description = "Devolve se o cartão deve aparecer e o estado de cada passo. Conta criada antes das boas-vindas, cartão dispensado e passos concluídos respondem visible=false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado do cartão"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping
    public OnboardingResponse get() {
        return service.get();
    }

    @Operation(summary = "Dispensa o cartão de primeiros passos", description = "Esconde o cartão em todos os aparelhos. Pode ser repetido sem efeito.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cartão dispensado"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss() {
        service.dismiss();
    }
}
