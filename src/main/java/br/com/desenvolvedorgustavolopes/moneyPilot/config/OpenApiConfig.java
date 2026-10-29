package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato público da API (/v3/api-docs). As rotas exigem o Bearer por padrão; as públicas
 * se marcam com {@code @SecurityRequirements} vazio no controller.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI moneyPilotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("moneyPilot API")
                        .version("v1")
                        .description("""
                                API do moneyPilot: contas, lançamentos, categorias, orçamentos, contas a pagar e receber, \
                                recorrências e relatórios. Valores em reais usam número com 2 casas e ponto decimal \
                                (50.00); datas usam aaaa-mm-dd.

                                Autenticação: entre em /api/v1/auth/login e mande o access token em \
                                Authorization: Bearer. Fora do navegador, peça o refresh token no corpo com \
                                refreshTokenInBody=true e renove em /api/v1/auth/refresh. Passo a passo e texto \
                                pronto para assistentes de IA: guia em /ajuda/usando-com-ia.html."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
