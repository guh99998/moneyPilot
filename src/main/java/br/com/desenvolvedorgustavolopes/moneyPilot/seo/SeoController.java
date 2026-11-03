package br.com.desenvolvedorgustavolopes.moneyPilot.seo;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.AppProperties;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code robots.txt} e {@code sitemap.xml} para os buscadores. São gerados com o endereço público configurado
 * ({@code app.public-url}), porque o sitemap exige URLs absolutas e o repositório não guarda o domínio.
 * O app em {@code /app/} e a API ficam de fora: são atrás de login.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class SeoController {

    private static final String HELP_PATTERN = "classpath:/static/ajuda/*.html";
    private static final String HELP_INDEX = "index.html";

    private final AppProperties appProperties;

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return """
                User-agent: *
                Allow: /
                Disallow: /app/
                Disallow: /api/

                Sitemap: %s/sitemap.xml
                """.formatted(appProperties.publicUrl());
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() throws IOException {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        for (String path : publicPaths()) {
            xml.append("  <url><loc>").append(appProperties.publicUrl()).append(path).append("</loc></url>\n");
        }
        xml.append("</urlset>\n");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml.toString());
    }

    /** O site, o índice da ajuda e cada página da ajuda; uma página nova entra sozinha. */
    private List<String> publicPaths() throws IOException {
        List<String> paths = new ArrayList<>(List.of("/", "/ajuda/"));
        List<String> help = new ArrayList<>();
        for (Resource page : new PathMatchingResourcePatternResolver().getResources(HELP_PATTERN)) {
            String name = page.getFilename();
            if (name != null && !name.equals(HELP_INDEX)) {
                help.add("/ajuda/" + name);
            }
        }
        help.sort(String::compareTo);
        paths.addAll(help);
        return paths;
    }
}
