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
import java.time.LocalDate;
import java.time.ZoneId;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class CatalogHttpIT {
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
    void publishedProgrammeIsPublicAndContainsFilmDetails() throws Exception {
        var response = get("/api/catalog/films");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Une nuit en musique"));
        assertTrue(response.body().contains("durationMinutes"));
        assertTrue(response.body().contains("synopsis"));
    }

    @Test
    void requestedDayReturnsAnnouncedPricesAndRoomNames() throws Exception {
        String tomorrow = LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(1).toString();
        var response =
                get(
                        "/api/catalog/films/10000000-0000-0000-0000-000000000001/screenings?date="
                                + tomorrow);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("roomName"));
        assertTrue(response.body().contains("\"priceCents\":1100"));
    }

    @Test
    void invalidDateReturnsBadRequest() throws Exception {
        assertEquals(
                400,
                get("/api/catalog/films/10000000-0000-0000-0000-000000000001/screenings?date=invalid")
                        .statusCode());
    }

    @Test
    void unknownFilmReturnsNotFound() throws Exception {
        assertEquals(
                404,
                get("/api/catalog/films/99999999-0000-0000-0000-000000000001/screenings")
                        .statusCode());
    }
}
