package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDTO createUser(@Valid @RequestBody RegisterRequest request) {
        return service.createUser(request);
    }

    @GetMapping("/{email}")
    @ResponseStatus(HttpStatus.OK)
    public UserDTO findUserByEmail(@PathVariable String email) {
        return service.getUserByEmail(email);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

}
