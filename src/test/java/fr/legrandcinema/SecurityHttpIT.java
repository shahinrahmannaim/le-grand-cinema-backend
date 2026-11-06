package fr.legrandcinema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.legrandcinema.booking.SeatEvents;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class SecurityHttpIT {
    @Value("${local.server.port}")
    int port;

    @Autowired SeatEvents events;
    private final ObjectMapper json = new ObjectMapper();

    private HttpClient client() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> write(
            HttpClient client, String method, String path, Object body, boolean csrf)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/json");
        if (csrf) {
            JsonNode token = json.readTree(get(client, "/api/auth/csrf").body());
            builder.header(token.get("headerName").asText(), token.get("token").asText());
        }
        return client.send(
                builder.method(
                                method,
                                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void login(HttpClient client, String role) throws Exception {
        assertEquals(
                200,
                write(
                                client,
                                "POST",
                                "/api/auth/login",
                                Map.of(
                                        "email",
                                        role + "@demo.cinema.test",
                                        "password",
                                        System.getenv("DEMO_PASSWORD")),
                                true)
                        .statusCode());
    }

    @Test
    void sessionAuthenticationCsrfAndLogoutWorkTogether() throws Exception {
        var c = client();
        assertEquals(401, get(c, "/api/auth/me").statusCode());
        assertEquals(
                403,
                write(
                                c,
                                "POST",
                                "/api/auth/login",
                                Map.of("email", "client@demo.cinema.test", "password", "bad"),
                                false)
                        .statusCode());
        assertEquals(
                401,
                write(
                                c,
                                "POST",
                                "/api/auth/login",
                                Map.of("email", "client@demo.cinema.test", "password", "bad"),
                                true)
                        .statusCode());
        login(c, "client");
        assertTrue(get(c, "/api/auth/me").body().contains("CLIENT"));
        assertEquals(403, get(c, "/api/admin/films").statusCode());
        assertEquals(403, get(c, "/api/staff/reservations").statusCode());
        assertEquals(403, write(c, "POST", "/api/staff/reservations", Map.of(), true).statusCode());
        assertEquals(204, write(c, "POST", "/api/auth/logout", Map.of(), true).statusCode());
        assertEquals(401, get(c, "/api/auth/me").statusCode());
    }

    @Test
    void registrationValidationAndDuplicateEmailAreHandled() throws Exception {
        var c = client();
        String email = UUID.randomUUID() + "@example.test";
        var body =
                Map.of(
                        "email",
                        email,
                        "password",
                        "Test-only-password-2026",
                        "displayName",
                        "Visiteur");
        assertEquals(200, write(c, "POST", "/api/auth/register", body, true).statusCode());
        assertEquals(409, write(c, "POST", "/api/auth/register", body, true).statusCode());
        assertEquals(
                400,
                write(
                                c,
                                "POST",
                                "/api/auth/register",
                                Map.of("email", "bad", "password", "tiny", "displayName", ""),
                                true)
                        .statusCode());
        assertEquals(
                200,
                write(
                                c,
                                "POST",
                                "/api/auth/login",
                                Map.of("email", email, "password", "Test-only-password-2026"),
                                true)
                        .statusCode());
        assertEquals(200, get(c, "/api/reservations").statusCode());
    }

    @Test
    void adminCanCreateProgrammeButOverlapAndReservedChangesAreBlocked() throws Exception {
        var c = client();
        login(c, "admin");
        var films = get(c, "/api/admin/films");
        assertEquals(200, films.statusCode());
        JsonNode rooms = json.readTree(get(c, "/api/admin/rooms").body());
        var film =
                Map.of(
                        "title",
                        "HTTP " + UUID.randomUUID(),
                        "synopsis",
                        "Test de programmation",
                        "durationMinutes",
                        90,
                        "genre",
                        "Test",
                        "posterUrl",
                        "",
                        "published",
                        true);
        var response = write(c, "POST", "/api/admin/films", film, true);
        assertEquals(200, response.statusCode());
        String filmId = json.readTree(response.body()).get("id").asText();
        var unused = write(c, "POST", "/api/admin/films", film, true);
        String unusedId = json.readTree(unused.body()).get("id").asText();
        assertEquals(
                204,
                write(c, "DELETE", "/api/admin/films/" + unusedId, Map.of(), true).statusCode());
        assertEquals(200, write(c, "PUT", "/api/admin/films/" + filmId, film, true).statusCode());
        var show =
                Map.of(
                        "filmId",
                        filmId,
                        "roomId",
                        rooms.get(0).get("id").asText(),
                        "startsAt",
                        java.time.Instant.now().plusSeconds(86400 * 90).toString(),
                        "priceCents",
                        1200);
        response = write(c, "POST", "/api/admin/screenings", show, true);
        assertEquals(200, response.statusCode());
        String showId = json.readTree(response.body()).get("id").asText();
        assertEquals(
                409, write(c, "DELETE", "/api/admin/films/" + filmId, Map.of(), true).statusCode());
        assertEquals(409, write(c, "POST", "/api/admin/screenings", show, true).statusCode());
        assertEquals(409, write(c, "PUT", "/api/admin/films/" + filmId, film, true).statusCode());
        JsonNode plan =
                json.readTree(get(c, "/api/catalog/screenings/" + showId + "/seats").body());
        var request =
                Map.of(
                        "screeningId",
                        showId,
                        "seatIds",
                        java.util.List.of(plan.get("seats").get(0).get("id").asText()),
                        "idempotencyKey",
                        UUID.randomUUID().toString());
        response = write(c, "POST", "/api/staff/reservations", request, true);
        assertEquals(200, response.statusCode());
        String ticketId = json.readTree(response.body()).get("id").asText();
        assertEquals(
                409, write(c, "PUT", "/api/admin/screenings/" + showId, show, true).statusCode());
        assertEquals(
                409,
                write(c, "POST", "/api/admin/screenings/" + showId + "/cancel", Map.of(), true)
                        .statusCode());
        assertEquals(
                200,
                write(
                                c,
                                "PATCH",
                                "/api/admin/films/" + filmId + "/publication",
                                Map.of("published", false),
                                true)
                        .statusCode());
        assertEquals(200, get(c, "/api/reservations/" + ticketId).statusCode());
        var other = client();
        login(other, "client");
        assertEquals(404, get(other, "/api/reservations/" + ticketId).statusCode());
    }

    @Test
    void staffSearchAndStatisticsValidatePeriodsAndDoNotLeakAccounts() throws Exception {
        var c = client();
        login(c, "staff");
        String day =
                java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).plusDays(1).toString();
        var r = get(c, "/api/staff/reservations?reference=NO-SUCH-REFERENCE");
        assertEquals(200, r.statusCode());
        assertEquals(0, json.readTree(r.body()).get("total").asInt());
        assertEquals(400, get(c, "/api/staff/reservations?page=-1").statusCode());
        var occupancy = get(c, "/api/staff/occupancy?from=" + day + "&to=" + day);
        assertEquals(200, occupancy.statusCode());
        java.time.Instant.parse(json.readTree(occupancy.body()).get(0).get("starts_at").asText());
        assertEquals(
                400,
                get(c, "/api/staff/occupancy?from=" + day + "&to=" + day + "&threshold=101")
                        .statusCode());
        var analytics = get(c, "/api/staff/analytics?from=" + day + "&to=" + day);
        assertEquals(200, analytics.statusCode());
        java.time.LocalDate.parse(
                json.readTree(analytics.body()).get("current").get(0).get("day").asText());
        assertEquals(
                400,
                get(c, "/api/staff/analytics?from=" + day + "&to=" + day + "&baselineFrom=" + day)
                        .statusCode());
        assertEquals(
                400,
                get(
                                c,
                                "/api/staff/analytics?from="
                                        + day
                                        + "&to="
                                        + day
                                        + "&baselineFrom="
                                        + day
                                        + "&baselineTo="
                                        + day)
                        .statusCode());
        assertEquals(403, get(c, "/api/admin/films").statusCode());
    }

    @Test
    void clientConfirmationReplaysAndSseRefreshesOnlyAfterCommittedBooking() throws Exception {
        var c = client();
        login(c, "client");
        String day =
                java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).plusDays(2).toString();
        JsonNode shows =
                json.readTree(
                        get(
                                        c,
                                        "/api/catalog/films/10000000-0000-0000-0000-000000000002/screenings?date="
                                                + day)
                                .body());
        String showId = shows.get(0).get("id").asText();
        JsonNode plan =
                json.readTree(get(c, "/api/catalog/screenings/" + showId + "/seats").body());
        var body =
                Map.of(
                        "screeningId",
                        showId,
                        "seatIds",
                        java.util.List.of(plan.get("seats").get(0).get("id").asText()),
                        "idempotencyKey",
                        UUID.randomUUID().toString());
        var stream =
                c.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/api/catalog/screenings/"
                                                        + showId
                                                        + "/events"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, stream.statusCode());
        try (var input = stream.body();
                var reader = new java.io.BufferedReader(new java.io.InputStreamReader(input));
                var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first =
                    pool.submit(
                            () ->
                                    reader.lines()
                                            .filter(line -> line.startsWith("event:"))
                                            .findFirst()
                                            .orElse(""));
            assertEquals("event:refresh", first.get(5, java.util.concurrent.TimeUnit.SECONDS));
            var afterCommit =
                    pool.submit(
                            () ->
                                    reader.lines()
                                            .filter(line -> line.startsWith("event:"))
                                            .findFirst()
                                            .orElse(""));
            var response = write(c, "POST", "/api/reservations", body, true);
            assertEquals(200, response.statusCode());
            assertEquals(
                    "event:refresh", afterCommit.get(5, java.util.concurrent.TimeUnit.SECONDS));
            JsonNode ticket = json.readTree(response.body());
            var repeated = write(c, "POST", "/api/reservations", body, true);
            assertEquals(200, repeated.statusCode());
            assertEquals(
                    ticket.get("id").asText(), json.readTree(repeated.body()).get("id").asText());
            assertEquals(
                    200, get(c, "/api/reservations/" + ticket.get("id").asText()).statusCode());
            assertTrue(
                    get(c, "/api/reservations").body().contains(ticket.get("reference").asText()));
            assertEquals(
                    400,
                    write(
                                    c,
                                    "POST",
                                    "/api/reservations",
                                    Map.of(
                                            "screeningId",
                                            showId,
                                            "seatIds",
                                            java.util.List.of(),
                                            "idempotencyKey",
                                            UUID.randomUUID().toString()),
                                    true)
                            .statusCode());
        } finally {
            events.shutdown();
        }
    }
}
