package br.com.desenvolvedorgustavolopes.moneyPilot.contribution;

import io.swagger.v3.oas.annotations.media.Schema;

public record PixResponse(
        @Schema(description = "Chave Pix aleatória que recebe o apoio ao projeto", example = "123e4567-e89b-12d3-a456-426614174000") String key,
        @Schema(description = "QR code da chave como data URI (png ou jpeg); vazio se não houver imagem configurada", example = "data:image/png;base64,iVBORw0KGgo=") String qrImage
) {
}
