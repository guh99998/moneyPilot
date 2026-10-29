package br.com.desenvolvedorgustavolopes.moneyPilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** A raiz é o site (static/index.html); o app vive em /app/ e a central de ajuda em /ajuda/. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/app", "/app/");
        registry.addViewController("/app/").setViewName("forward:/app/index.html");
        registry.addRedirectViewController("/ajuda", "/ajuda/");
        registry.addViewController("/ajuda/").setViewName("forward:/ajuda/index.html");
    }
}
