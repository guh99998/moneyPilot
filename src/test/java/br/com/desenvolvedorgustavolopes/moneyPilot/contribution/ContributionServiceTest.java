package br.com.desenvolvedorgustavolopes.moneyPilot.contribution;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.PixProperties;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ContributionNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bordas da imagem do QR code: qualquer problema vira "sem imagem", e a chave continua sendo entregue. */
class ContributionServiceTest {

    private static final String KEY = "00000000-0000-4000-8000-000000000000";

    @TempDir
    Path dir;

    private ContributionService service(String qrFile) {
        return new ContributionService(new PixProperties(KEY, qrFile));
    }

    @Test
    void withoutKey_isNotConfigured() {
        ContributionService service = new ContributionService(new PixProperties("  ", null));

        assertThrows(ContributionNotConfiguredException.class, service::pix);
    }

    @Test
    void withoutQrFile_returnsOnlyTheKey() {
        PixResponse response = service("").pix();

        assertEquals(KEY, response.key());
        assertNull(response.qrImage());
    }

    @Test
    void missingFile_returnsOnlyTheKey() {
        assertNull(service(dir.resolve("nao-existe.png").toString()).pix().qrImage());
    }

    @Test
    void fileAboveTheLimit_isIgnored() throws IOException {
        Path big = Files.write(dir.resolve("grande.png"), new byte[(int) ContributionService.MAX_QR_BYTES + 1]);

        assertNull(service(big.toString()).pix().qrImage());
    }

    @Test
    void fileThatIsNotPngOrJpeg_isIgnored() throws IOException {
        Path other = Files.write(dir.resolve("qr.svg"), new byte[]{1, 2, 3});

        assertNull(service(other.toString()).pix().qrImage());
    }

    @Test
    void jpegAndPng_areSentAsDataUri() throws IOException {
        Path jpg = Files.write(dir.resolve("qr.JPG"), new byte[]{1, 2, 3});
        Path png = Files.write(dir.resolve("qr.png"), new byte[]{1, 2, 3});

        assertTrue(service(jpg.toString()).pix().qrImage().startsWith("data:image/jpeg;base64,"));
        assertTrue(service(png.toString()).pix().qrImage().startsWith("data:image/png;base64,"));
    }
}
