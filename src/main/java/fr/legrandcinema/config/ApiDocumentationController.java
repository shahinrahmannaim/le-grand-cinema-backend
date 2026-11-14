package fr.legrandcinema.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class ApiDocumentationController {
    @GetMapping({"/swagger-ui.html", "/api/docs"})
    ResponseEntity<Void> index() {
        return ResponseEntity.status(302).location(URI.create("/api/docs/index.html")).build();
    }

    @GetMapping(value = "/api/docs/openapi.yaml", produces = "application/yaml")
    Resource specification() {
        return new ClassPathResource("api-docs/openapi.yaml");
    }
}
