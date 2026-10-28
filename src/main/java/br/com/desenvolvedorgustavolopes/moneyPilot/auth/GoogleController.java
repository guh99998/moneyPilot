package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.config.GoogleProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GoogleController {

    private final GoogleSignInService service;
    private final GoogleProperties properties;
    private final RefreshCookies refreshCookies;

    @GetMapping("/auth/google/config")
    public GoogleConfigResponse config() {
        return new GoogleConfigResponse(properties.clientId());
    }

    @PostMapping("/auth/google")
    public GoogleSignInResponse signIn(@Valid @RequestBody GoogleSignInRequest request,
                                       HttpServletRequest httpRequest, HttpServletResponse response) {
        GoogleSignInService.SignInResult result = service.signIn(request.credential(), httpRequest.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookies.build(result.tokens().refreshToken(), RefreshTokenService.REFRESH_TTL).toString());
        return new GoogleSignInResponse(result.tokens().accessToken(), result.email());
    }

    @PostMapping("/profile/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void link(@Valid @RequestBody LinkGoogleRequest request) {
        service.link(request.credential(), request.currentPassword());
    }

    // POST com corpo, em vez de DELETE com corpo: a senha atual vai junto.
    @PostMapping("/profile/google/unlink")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@Valid @RequestBody UnlinkGoogleRequest request) {
        service.unlink(request.currentPassword());
    }

    @DeleteMapping("/profile/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideAvatar() {
        service.hideAvatar();
    }

    @PostMapping("/profile/avatar/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void useGoogleAvatar() {
        service.useGoogleAvatar();
    }
}
