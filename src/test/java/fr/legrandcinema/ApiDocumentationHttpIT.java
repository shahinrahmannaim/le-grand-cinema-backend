package fr.legrandcinema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class ApiDocumentationHttpIT {
    @Value("${local.server.port}")
    int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void swaggerEntryPointAndSpecificationArePublic() throws Exception {
        var redirect = get("/swagger-ui.html");
        assertEquals(302, redirect.statusCode());
        assertEquals(
                "/api/docs/index.html", redirect.headers().firstValue("Location").orElseThrow());
        var page = get("/api/docs/index.html");
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("/api/docs/assets/swagger-ui-bundle.js"));
        var specification = get("/api/docs/openapi.yaml");
        assertEquals(200, specification.statusCode());
        assertTrue(specification.body().contains("openapi: 3.0.3"));
        assertTrue(specification.body().contains("/reservations:"));
        assertTrue(specification.body().contains("/admin/films:"));
    }

    @Test
    void bundledSwaggerAssetsAndCsrfInitializerAreAvailable() throws Exception {
        assertEquals(200, get("/api/docs/assets/swagger-ui-bundle.js").statusCode());
        assertEquals(200, get("/api/docs/assets/swagger-ui.css").statusCode());
        var initializer = get("/api/docs/swagger-initializer.js");
        assertEquals(200, initializer.statusCode());
        assertTrue(initializer.body().contains("/api/auth/csrf"));
        assertTrue(initializer.body().contains("validatorUrl: null"));
    }

    @Test
    void documentationDoesNotBypassApiAuthenticationOrCsrf() throws Exception {
        assertEquals(401, get("/api/auth/me").statusCode());
        assertEquals(401, get("/api/admin/films").statusCode());
        var response =
                HttpClient.newHttpClient()
                        .send(
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://127.0.0.1:"
                                                                + port
                                                                + "/api/auth/login"))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }
}
