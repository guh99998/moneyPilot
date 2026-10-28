package br.com.desenvolvedorgustavolopes.moneyPilot.support;

import br.com.desenvolvedorgustavolopes.moneyPilot.auth.AuthenticatedUserProvider;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.UnauthenticatedRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// Pública com login opcional: quem não consegue entrar também precisa falar com o suporte.
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService service;
    private final AuthenticatedUserProvider authenticatedUser;

    @PostMapping("/tickets")
    public SupportTicketResponse send(@Valid @RequestBody SupportTicketRequest request, HttpServletRequest httpRequest) {
        // Token enviado mas recusado (expirado): 401 faz o cliente renovar e reenviar, em vez de tratar como anônimo.
        String authorization = httpRequest.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ") && authenticatedUser.findCurrentUserId().isEmpty()) {
            throw new UnauthenticatedRequestException();
        }
        return service.send(request, httpRequest.getRemoteAddr());
    }
}
