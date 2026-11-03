package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** A raiz é o site (static/index.html); o app vive em /app/, a ajuda em /ajuda/ e as páginas legais em /privacidade/ e /termos/. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/app", "/app/");
        registry.addViewController("/app/").setViewName("forward:/app/index.html");
        registry.addRedirectViewController("/ajuda", "/ajuda/");
        registry.addViewController("/ajuda/").setViewName("forward:/ajuda/index.html");
        registry.addRedirectViewController("/privacidade", "/privacidade/");
        registry.addViewController("/privacidade/").setViewName("forward:/privacidade/index.html");
        registry.addRedirectViewController("/termos", "/termos/");
        registry.addViewController("/termos/").setViewName("forward:/termos/index.html");
    }
}
