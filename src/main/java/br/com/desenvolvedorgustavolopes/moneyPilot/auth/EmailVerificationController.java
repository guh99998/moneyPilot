package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// Públicas: quem ainda não verificou a conta não consegue abrir sessão.
@RestController
@RequestMapping("/api/v1/auth/verification")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService service;

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody ConfirmVerificationRequest request) {
        service.confirm(request.token());
    }

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest httpRequest) {
        service.resend(request.email(), httpRequest.getRemoteAddr());
    }
}
