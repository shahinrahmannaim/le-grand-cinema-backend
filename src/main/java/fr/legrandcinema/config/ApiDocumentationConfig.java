package fr.legrandcinema.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiDocumentationConfig implements WebMvcConfigurer {
    private final String swaggerVersion;

    public ApiDocumentationConfig(@Value("${cinema.swagger-ui-version}") String swaggerVersion) {
        this.swaggerVersion = swaggerVersion;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/docs/assets/**")
                .addResourceLocations(
                        "classpath:/META-INF/resources/webjars/swagger-ui/" + swaggerVersion + "/");
    }
}
