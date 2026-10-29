package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code key}: chave Pix aleatória que recebe o apoio ao projeto (PIX_KEY). {@code qrFile}: caminho, no servidor, da
 * imagem do QR code dessa chave (PIX_QR_FILE). As duas vêm do ambiente, nunca do código: o repositório é público.
 * Sem chave, o apoio inteiro some.
 */
@ConfigurationProperties(prefix = "app.pix")
public record PixProperties(String key, String qrFile) {

    public PixProperties {
        key = key == null ? "" : key.trim();
        qrFile = qrFile == null ? "" : qrFile.trim();
    }

    public boolean enabled() {
        return !key.isEmpty();
    }
}
