package br.com.desenvolvedorgustavolopes.moneyPilot.contribution;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Apoio ao projeto", description = "Chave Pix para quem quiser ajudar a manter o projeto, que é gratuito. O app não processa nem registra pagamentos.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contribution")
public class ContributionController {

    private final ContributionService service;

    @Operation(summary = "Busca a chave Pix de apoio", description = "Devolve a chave Pix aleatória e, se houver, o QR code como imagem embutida. Rota pública, sem login: a chave existe para ser mostrada a quem quer apoiar. Sem chave configurada no servidor, responde 404.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chave e QR code"),
            @ApiResponse(responseCode = "404", description = "O apoio não está configurado neste servidor", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/pix")
    public PixResponse pix() {
        return service.pix();
    }
}
