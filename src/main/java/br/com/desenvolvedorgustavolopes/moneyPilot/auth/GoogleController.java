package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import br.com.desenvolvedorgustavolopes.moneyPilot.config.GoogleProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Google", description = "Entrar com Google e vincular a conta.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GoogleController {

    private final GoogleSignInService service;
    private final GoogleProperties properties;
    private final RefreshCookies refreshCookies;

    @Operation(summary = "Configuração do Google", description = "Devolve o client id para o botão Entrar com Google.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client id do Google")
    })
    @SecurityRequirements
    @GetMapping("/auth/google/config")
    public GoogleConfigResponse config() {
        return new GoogleConfigResponse(properties.clientId());
    }

    @Operation(summary = "Entra com Google", description = "Valida a credencial do Google e abre a sessão, criando a conta se não existir. O refresh token vai no cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão aberta"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas; veja o cabeçalho Retry-After", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @SecurityRequirements
    @PostMapping("/auth/google")
    public GoogleSignInResponse signIn(@Valid @RequestBody GoogleSignInRequest request,
                                       HttpServletRequest httpRequest, HttpServletResponse response) {
        GoogleSignInService.SignInResult result = service.signIn(request.credential(), httpRequest.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookies.build(result.tokens().refreshToken(), RefreshTokenService.REFRESH_TTL).toString());
        return new GoogleSignInResponse(result.tokens().accessToken(), result.email());
    }

    @Operation(summary = "Vincula o Google", description = "Vincula a conta do Google à conta atual. Exige a senha atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Google vinculado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/profile/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void link(@Valid @RequestBody LinkGoogleRequest request) {
        service.link(request.credential(), request.currentPassword());
    }

    // POST com corpo, em vez de DELETE com corpo: a senha atual vai junto.
    @Operation(summary = "Desvincula o Google", description = "Remove o vínculo com o Google. Exige a senha atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Google desvinculado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @PostMapping("/profile/google/unlink")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@Valid @RequestBody UnlinkGoogleRequest request) {
        service.unlink(request.currentPassword());
    }

    @Operation(summary = "Esconde o avatar", description = "Para de mostrar a foto do Google.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Avatar escondido"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @DeleteMapping("/profile/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideAvatar() {
        service.hideAvatar();
    }

    @Operation(summary = "Usa o avatar do Google", description = "Volta a mostrar a foto do Google.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Avatar do Google em uso"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @PostMapping("/profile/avatar/google")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void useGoogleAvatar() {
        service.useGoogleAvatar();
    }
}
