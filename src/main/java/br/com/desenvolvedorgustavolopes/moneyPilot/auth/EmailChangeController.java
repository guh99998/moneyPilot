package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmailChangeController {

    private final EmailChangeService service;

    @PostMapping("/profile/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestEmailChange(@Valid @RequestBody EmailChangeRequest request) {
        service.request(request);
    }

    // Fica sob /auth porque o cookie refresh_token só é enviado a essa árvore de rotas.
    // Pública: o link do e-mail funciona sem sessão aberta.
    @PostMapping("/auth/email/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody ConfirmEmailChangeRequest request,
                                   @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        service.confirm(request.token(), refreshToken);
    }
}
