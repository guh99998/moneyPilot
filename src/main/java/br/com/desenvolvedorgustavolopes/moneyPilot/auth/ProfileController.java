package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService service;

    @GetMapping("/profile")
    public ProfileResponse getProfile() {
        return service.getProfile();
    }

    @PutMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return service.updateName(request);
    }

    // Fica sob /auth porque o cookie refresh_token só é enviado a essa árvore de rotas.
    @PostMapping("/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        service.changePassword(request, refreshToken);
    }
}
