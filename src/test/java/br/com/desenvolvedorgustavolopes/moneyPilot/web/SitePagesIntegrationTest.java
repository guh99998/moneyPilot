package br.com.desenvolvedorgustavolopes.moneyPilot.web;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec 020: o site na raiz e o app em /app/, ambos sem exigir login. O MockMvc não executa o forward, então o teste
 * confere o destino; o conteúdo das duas páginas é coberto por static-pages.test.mjs.
 */
class SitePagesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // CA1, CA7
    @Test
    void root_servesTheSiteWithoutLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    // CA7
    @Test
    void app_redirectsToTheTrailingSlash() throws Exception {
        mockMvc.perform(get("/app"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/"));
    }

    // CA7
    @Test
    void appSlash_servesTheAppWithoutLogin() throws Exception {
        mockMvc.perform(get("/app/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/app/index.html"));
    }

    // Spec 021 CA1
    @Test
    void ajuda_redirectsToTheTrailingSlash() throws Exception {
        mockMvc.perform(get("/ajuda"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ajuda/"));
    }

    // Spec 021 CA1
    @Test
    void ajudaSlash_servesTheIndexWithoutLogin() throws Exception {
        mockMvc.perform(get("/ajuda/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/ajuda/index.html"));
    }

    // Spec 021 CA1
    @Test
    void tutorial_isReadableWithoutLogin() throws Exception {
        mockMvc.perform(get("/ajuda/contas.html"))
                .andExpect(status().isOk());
    }

    // CA7
    @Test
    void helpPath_isNotBehindLogin() throws Exception {
        int status = mockMvc.perform(get("/ajuda/")).andReturn().getResponse().getStatus();
        assertNotEquals(401, status);
    }

    // Spec 024 CA1
    @Test
    void privacyAndTerms_redirectToTheTrailingSlash() throws Exception {
        mockMvc.perform(get("/privacidade")).andExpect(redirectedUrl("/privacidade/"));
        mockMvc.perform(get("/termos")).andExpect(redirectedUrl("/termos/"));
    }

    // Spec 024 CA1
    @Test
    void privacyAndTerms_areReadableWithoutLogin() throws Exception {
        mockMvc.perform(get("/privacidade/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/privacidade/index.html"));
        mockMvc.perform(get("/termos/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/termos/index.html"));
        mockMvc.perform(get("/privacidade/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/termos/index.html")).andExpect(status().isOk());
    }

    // Spec 024: as fontes do site são públicas; sem isso a página cai na fonte de sistema
    @Test
    void fonts_areServedWithoutLogin() throws Exception {
        mockMvc.perform(get("/fonts/manrope-latin.woff2")).andExpect(status().isOk());
        mockMvc.perform(get("/fonts/bricolage-grotesque-latin.woff2")).andExpect(status().isOk());
    }

    // Um recurso público que não existe é 404, não 500.
    @Test
    void missingPublicResource_isNotFound() throws Exception {
        mockMvc.perform(get("/css/nao-existe.css"))
                .andExpect(status().isNotFound());
    }
}
