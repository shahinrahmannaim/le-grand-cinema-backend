package fr.legrandcinema.catalog;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CinemaController {
    private final JdbcTemplate jdbc;

    public CinemaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public CinemaDetails home() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) AS room_count, COALESCE(SUM(capacity), 0) AS seat_capacity FROM room",
                (rs, row) ->
                        new CinemaDetails(
                                "Le Grand Cinéma",
                                rs.getInt("room_count"),
                                rs.getInt("seat_capacity"),
                                "Europe/Paris",
                                "/api/docs/index.html"));
    }

    public record CinemaDetails(
            String name,
            int roomCount,
            int seatCapacity,
            String timeZone,
            String documentation) {}
}
