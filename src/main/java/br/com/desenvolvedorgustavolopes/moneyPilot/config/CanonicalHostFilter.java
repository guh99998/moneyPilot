package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Redireciona qualquer host diferente do oficial (ex.: o *.herokuapp.com) para ele.
 * Sem app.canonical-host configurado, não faz nada — local e testes seguem normais.
 * 308 em vez de 301 para POST/PUT continuarem POST/PUT depois do redirect.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CanonicalHostFilter extends OncePerRequestFilter {

    private final String canonicalHost;

    public CanonicalHostFilter(@Value("${app.canonical-host:}") String canonicalHost) {
        this.canonicalHost = canonicalHost.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return canonicalHost.isEmpty() || canonicalHost.equalsIgnoreCase(request.getServerName());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String location = "https://" + canonicalHost + request.getRequestURI();
        if (request.getQueryString() != null) {
            location += "?" + request.getQueryString();
        }

        response.setStatus(HttpServletResponse.SC_PERMANENT_REDIRECT);
        response.setHeader(HttpHeaders.LOCATION, location);
    }
}
