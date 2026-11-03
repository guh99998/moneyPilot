package br.com.desenvolvedorgustavolopes.moneyPilot.seo;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import br.com.desenvolvedorgustavolopes.moneyPilot.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** robots.txt e sitemap.xml: públicos, com URLs absolutas do endereço configurado, sem o app nem a API. */
class SeoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppProperties appProperties;

    @Test
    void robots_isPublicPlainTextThatBlocksTheAppAndPointsToTheSitemap() throws Exception {
        String body = mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("User-agent: *"));
        assertTrue(body.contains("Disallow: /app/"));
        assertTrue(body.contains("Disallow: /api/"));
        assertTrue(body.contains("Sitemap: " + appProperties.publicUrl() + "/sitemap.xml"));
    }

    @Test
    void sitemap_isPublicXmlWithTheSiteAndEveryHelpPage() throws Exception {
        String body = mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn().getResponse().getContentAsString();

        String base = appProperties.publicUrl();
        assertTrue(body.contains("<loc>" + base + "/</loc>"));
        assertTrue(body.contains("<loc>" + base + "/ajuda/</loc>"));
        assertTrue(body.contains("<loc>" + base + "/privacidade/</loc>"));
        assertTrue(body.contains("<loc>" + base + "/termos/</loc>"));
        assertTrue(body.contains("<loc>" + base + "/ajuda/perfil-e-seguranca.html</loc>"));
        assertFalse(body.contains("/ajuda/index.html"));
        assertFalse(body.contains("/app/"));
    }

    @Test
    void sitemap_listsEveryHelpPageOnDisk() throws Exception {
        String body = mockMvc.perform(get("/sitemap.xml")).andReturn().getResponse().getContentAsString();

        try (Stream<Path> pages = Files.list(Path.of("src/main/resources/static/ajuda"))) {
            pages.map(page -> page.getFileName().toString())
                    .filter(name -> name.endsWith(".html") && !name.equals("index.html"))
                    .forEach(name -> assertTrue(body.contains("/ajuda/" + name + "</loc>"), name));
        }
    }
}
