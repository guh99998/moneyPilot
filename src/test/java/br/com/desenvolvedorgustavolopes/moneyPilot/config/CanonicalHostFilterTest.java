package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalHostFilterTest {

    private static final String CANONICAL = "moneypilot.desenvolvedorgustavolopes.com.br";

    @Test
    void redirectsOtherHostKeepingPathAndQuery() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/transactions");
        request.setServerName("money-pilot-3ff32d929aca.herokuapp.com");
        request.setQueryString("page=2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new CanonicalHostFilter(CANONICAL).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(308);
        assertThat(response.getHeader("Location"))
                .isEqualTo("https://" + CANONICAL + "/api/v1/transactions?page=2");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void passesThroughCanonicalHost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServerName(CANONICAL);
        MockFilterChain chain = new MockFilterChain();

        new CanonicalHostFilter(CANONICAL).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doesNothingWhenNotConfigured() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setServerName("localhost");
        MockFilterChain chain = new MockFilterChain();

        new CanonicalHostFilter("").doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }
}
