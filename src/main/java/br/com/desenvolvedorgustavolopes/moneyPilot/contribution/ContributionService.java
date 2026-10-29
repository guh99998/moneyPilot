package br.com.desenvolvedorgustavolopes.moneyPilot.contribution;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.PixProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ContributionNotConfiguredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

/**
 * O apoio é só mostrar a chave e o QR code: o pagamento acontece fora do app e nada sobre quem contribui é guardado.
 * A imagem é lida a cada chamada, para trocar o arquivo sem reiniciar. Qualquer problema com ela vira "sem imagem",
 * nunca um erro para a pessoa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionService {

    static final long MAX_QR_BYTES = 512 * 1024;

    private final PixProperties pix;

    public PixResponse pix() {
        if (!pix.enabled()) {
            throw new ContributionNotConfiguredException();
        }
        return new PixResponse(pix.key(), qrImage());
    }

    private String qrImage() {
        if (pix.qrFile().isEmpty()) {
            return null;
        }
        try {
            Path file = Path.of(pix.qrFile());
            String mime = mimeOf(file);
            if (mime == null) {
                log.warn("PIX_QR_FILE precisa ser png ou jpg; ignorando a imagem");
                return null;
            }
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_QR_BYTES) {
                log.warn("Imagem do QR code do Pix ausente ou acima de {} bytes; ignorando", MAX_QR_BYTES);
                return null;
            }
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (IOException | RuntimeException e) {
            log.warn("Não foi possível ler a imagem do QR code do Pix; ignorando", e);
            return null;
        }
    }

    private static String mimeOf(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return null;
    }
}
