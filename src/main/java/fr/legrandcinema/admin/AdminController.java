package fr.legrandcinema.admin;

import fr.legrandcinema.shared.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    public record FilmInput(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String synopsis,
            @Min(1) @Max(600) int durationMinutes,
            @NotBlank @Size(max = 60) String genre,
            @Size(max = 500) String posterUrl,
            boolean published) {}

    public record ShowInput(
            @NotNull UUID filmId,
            @NotNull UUID roomId,
            @NotNull Instant startsAt,
            @Min(0) @Max(100000) int priceCents) {}

    public record Publication(boolean published) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AdminController(JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    @GetMapping("/films")
    List<Map<String, Object>> films() {
        return jdbc.queryForList("SELECT * FROM film ORDER BY title,id");
    }

    @GetMapping("/rooms")
    List<Map<String, Object>> rooms() {
        return jdbc.queryForList("SELECT * FROM room ORDER BY name");
    }

    @GetMapping("/screenings")
    List<Map<String, Object>> shows() {
        return jdbc.queryForList(
                """
                SELECT s.*,f.title,r.name AS room_name FROM screening s JOIN film f ON f.id=s.film_id JOIN room r ON r.id=s.room_id
                WHERE s.starts_at>=? ORDER BY s.starts_at,s.id LIMIT 500
                """,
                Timestamp.from(clock.instant()));
    }

    @PostMapping("/films")
    Map<String, UUID> create(@Valid @RequestBody FilmInput input) {
        validatePoster(input.posterUrl());
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO film(id,title,synopsis,duration_minutes,genre,poster_url,published)"
                        + " VALUES(?,?,?,?,?,?,?)",
                id,
                input.title().trim(),
                input.synopsis().trim(),
                input.durationMinutes(),
                input.genre().trim(),
                input.posterUrl(),
                input.published());
        return Map.of("id", id);
    }

    @PutMapping("/films/{id}")
    Map<String, UUID> edit(@PathVariable UUID id, @Valid @RequestBody FilmInput input) {
        validatePoster(input.posterUrl());
        return tx.execute(
                status -> {
                    jdbc.queryForObject(
                            "SELECT id FROM film WHERE id=? FOR UPDATE", UUID.class, id);
                    Long count =
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM screening WHERE film_id=?",
                                    Long.class,
                                    id);
                    if (count != null && count > 0)
                        throw new ApiException(
                                409,
                                "Film déjà programmé : seul son état de publication peut changer."
                                        + " Créez une nouvelle fiche pour modifier son contenu.");
                    jdbc.update(
                            "UPDATE film SET"
                                + " title=?,synopsis=?,duration_minutes=?,genre=?,poster_url=?,published=?"
                                + " WHERE id=?",
                            input.title().trim(),
                            input.synopsis().trim(),
                            input.durationMinutes(),
                            input.genre().trim(),
                            input.posterUrl(),
                            input.published(),
                            id);
                    return Map.of("id", id);
                });
    }

    @DeleteMapping("/films/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteFilm(@PathVariable UUID id) {
        tx.executeWithoutResult(
                status -> {
                    jdbc.queryForObject(
                            "SELECT id FROM film WHERE id=? FOR UPDATE", UUID.class, id);
                    Long scheduled =
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM screening WHERE film_id=?",
                                    Long.class,
                                    id);
                    if (scheduled != null && scheduled > 0)
                        throw new ApiException(
                                409,
                                "Film déjà programmé : dépubliez-le pour conserver l’historique.");
                    jdbc.update("DELETE FROM film WHERE id=?", id);
                });
    }

    @PatchMapping("/films/{id}/publication")
    void publication(@PathVariable UUID id, @RequestBody Publication input) {
        if (jdbc.update("UPDATE film SET published=? WHERE id=?", input.published(), id) != 1)
            throw new java.util.NoSuchElementException();
    }

    @PostMapping("/screenings")
    Map<String, UUID> createShow(@Valid @RequestBody ShowInput input) {
        return saveShow(UUID.randomUUID(), input, false);
    }

    @PutMapping("/screenings/{id}")
    Map<String, UUID> editShow(@PathVariable UUID id, @Valid @RequestBody ShowInput input) {
        return saveShow(id, input, true);
    }

    @PostMapping("/screenings/{id}/cancel")
    void cancel(@PathVariable UUID id) {
        tx.executeWithoutResult(
                status -> {
                    lockUnbooked(id);
                    jdbc.update("UPDATE screening SET cancelled=true WHERE id=?", id);
                });
    }

    private Map<String, UUID> saveShow(UUID id, ShowInput input, boolean edit) {
        if (!input.startsAt().isAfter(clock.instant())
                || input.startsAt().isAfter(clock.instant().plusSeconds(366L * 86400 * 2)))
            throw new ApiException(
                    400, "Choisissez une séance future dans les deux prochaines années.");
        return tx.execute(
                status -> {
                    if (edit) lockUnbooked(id);
                    // Shared film lock prevents a duration edit racing with first scheduling.
                    Integer duration =
                            jdbc.queryForObject(
                                    "SELECT duration_minutes FROM film WHERE id=? FOR SHARE",
                                    Integer.class,
                                    input.filmId());
                    Integer turnaround =
                            jdbc.queryForObject(
                                    "SELECT turnaround_minutes FROM room WHERE id=?",
                                    Integer.class,
                                    input.roomId());
                    Instant end = input.startsAt().plusSeconds(duration * 60L),
                            until = end.plusSeconds(turnaround * 60L);
                    if (edit)
                        jdbc.update(
                                "UPDATE screening SET"
                                    + " film_id=?,room_id=?,starts_at=?,ends_at=?,occupied_until=?,price_cents=?"
                                    + " WHERE id=?",
                                input.filmId(),
                                input.roomId(),
                                Timestamp.from(input.startsAt()),
                                Timestamp.from(end),
                                Timestamp.from(until),
                                input.priceCents(),
                                id);
                    else
                        jdbc.update(
                                "INSERT INTO"
                                    + " screening(id,film_id,room_id,starts_at,ends_at,occupied_until,price_cents)"
                                    + " VALUES(?,?,?,?,?,?,?)",
                                id,
                                input.filmId(),
                                input.roomId(),
                                Timestamp.from(input.startsAt()),
                                Timestamp.from(end),
                                Timestamp.from(until),
                                input.priceCents());
                    return Map.of("id", id);
                });
    }

    private void lockUnbooked(UUID id) {
        if (jdbc.query(
                        "SELECT id FROM screening WHERE id=? FOR UPDATE",
                        (rs, n) -> rs.getObject(1, UUID.class),
                        id)
                .isEmpty()) throw new java.util.NoSuchElementException();
        Long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM reservation WHERE screening_id=? AND"
                                + " status='CONFIRMED'",
                        Long.class,
                        id);
        if (count != null && count > 0)
            throw new ApiException(
                    409,
                    "Séance réservée : modification et annulation bloquées pour préserver les"
                            + " billets.");
    }

    private void validatePoster(String url) {
        if (url != null && !url.isBlank() && (!url.startsWith("https://") || url.contains(" ")))
            throw new ApiException(400, "L’affiche doit utiliser une URL HTTPS.");
    }
}
