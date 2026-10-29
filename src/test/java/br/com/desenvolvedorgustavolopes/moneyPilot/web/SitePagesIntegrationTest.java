package br.com.desenvolvedorgustavolopes.moneyPilot.web;

import br.com.desenvolvedorgustavolopes.moneyPilot.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 020: o site na raiz e o app em /app/, ambos sem exigir login. */
class SitePagesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // CA1, CA7
    @Test
    void root_servesTheSiteWithoutLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-page=\"site\"")));
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"app\"")));
    }

    // CA7
    @Test
    void helpPath_isNotBehindLogin() throws Exception {
        int status = mockMvc.perform(get("/ajuda/")).andReturn().getResponse().getStatus();
        assertNotEquals(401, status);
    }

    // CA7
    @Test
    void privacyPath_isNotBehindLogin() throws Exception {
        int status = mockMvc.perform(get("/privacidade/")).andReturn().getResponse().getStatus();
        assertNotEquals(401, status);
    }
}
