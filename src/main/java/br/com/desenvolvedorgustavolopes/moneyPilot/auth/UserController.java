package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Tag(name = "Autenticação", description = "Cadastro, login e sessão. Para usar a API fora do navegador, entre com refreshTokenInBody=true: veja a página /app/#/usando-com-ia.")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;
    private final AuthService authService;
    private final RefreshCookies refreshCookies;
    private final JwtService jwtService;

    @Operation(summary = "Cadastra uma conta", description = "Cria a conta e envia o e-mail de verificação. Responde 202 mesmo se o e-mail já existir, para não revelar quem está cadastrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Cadastro aceito"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void createUser(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        service.createUser(request, httpRequest.getRemoteAddr());
    }

    @Operation(summary = "Entra com e-mail e senha", description = "Devolve o access token (vale poucos minutos). No navegador, o refresh token vai só num cookie httpOnly. Com refreshTokenInBody=true, ele vem no corpo, junto com expiresIn, e nenhum cookie é enviado. Conta só com Google não tem senha: use Criar senha no perfil antes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão aberta"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Operação não permitida", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthTokens authTokens = authService.login(request, httpRequest.getRemoteAddr());
        return respond(authTokens, request.wantsRefreshTokenInBody(), response);
    }

    @Operation(summary = "Renova a sessão", description = "Troca o refresh token por um novo par; o antigo deixa de valer. Mande {refreshToken} no corpo (modo corpo) ou use o cookie (navegador). Se vierem os dois, vale o corpo. Reusar um token já trocado encerra todas as sessões da pessoa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão renovada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse refresh(@CookieValue(name = "refresh_token", required = false) String cookieToken,
                                @RequestBody(required = false) RefreshRequest body, HttpServletResponse response) {
        String bodyToken = bodyToken(body);
        AuthTokens authTokens = authService.refresh(bodyToken != null ? bodyToken : cookieToken);
        return respond(authTokens, bodyToken != null, response);
    }

    @Operation(summary = "Sai", description = "Revoga o refresh token, vindo do corpo {refreshToken} ou do cookie. Responde 204 mesmo sem token válido.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sessão encerrada"),
            @ApiResponse(responseCode = "400", description = "Corpo ilegível", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = "refresh_token", required = false) String cookieToken,
                       @RequestBody(required = false) RefreshRequest body, HttpServletResponse response) {
        String bodyToken = bodyToken(body);
        authService.logout(bodyToken != null ? bodyToken : cookieToken);
        if (bodyToken == null) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookies.build("", Duration.ZERO).toString());
        }
    }

    // Modo corpo: o refresh token vai na resposta e nenhum cookie é enviado. Modo navegador: só o cookie.
    private AuthResponse respond(AuthTokens tokens, boolean inBody, HttpServletResponse response) {
        if (inBody) {
            return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), jwtService.expirationSeconds());
        }
        ResponseCookie cookie = refreshCookies.build(tokens.refreshToken(), RefreshTokenService.REFRESH_TTL);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return new AuthResponse(tokens.accessToken());
    }

    private static String bodyToken(RefreshRequest body) {
        return body == null || body.refreshToken() == null || body.refreshToken().isBlank() ? null : body.refreshToken();
    }

}
