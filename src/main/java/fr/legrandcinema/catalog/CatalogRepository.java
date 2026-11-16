package fr.legrandcinema.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;

    public CatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Film> findPublishedFilms(Instant now) {
        return jdbc.query(
                """
                SELECT f.* FROM film f
                WHERE f.published AND EXISTS (
                  SELECT 1 FROM screening s WHERE s.film_id=f.id AND NOT s.cancelled AND s.starts_at > ?
                ) ORDER BY lower(f.title), f.id
                """,
                this::mapFilm,
                Timestamp.from(now));
    }

    public Optional<Film> findPublishedFilm(UUID id) {
        return jdbc.query("SELECT * FROM film WHERE id=? AND published", this::mapFilm, id).stream()
                .findFirst();
    }

    public List<Screening> findScreenings(UUID filmId, DateWindow window, Instant now) {
        return jdbc.query(
                """
                SELECT s.id, s.film_id, r.name AS room_name, s.starts_at, s.price_cents
                FROM screening s JOIN room r ON r.id=s.room_id JOIN film f ON f.id=s.film_id
                WHERE s.film_id=? AND f.published AND NOT s.cancelled
                  AND s.starts_at >= ? AND s.starts_at < ? AND s.starts_at > ?
                ORDER BY s.starts_at, r.name, s.id
                """,
                (rs, row) ->
                        new Screening(
                                rs.getObject("id", UUID.class),
                                rs.getObject("film_id", UUID.class),
                                rs.getString("room_name"),
                                rs.getTimestamp("starts_at").toInstant(),
                                rs.getInt("price_cents")),
                filmId,
                Timestamp.from(window.from()),
                Timestamp.from(window.until()),
                Timestamp.from(now));
    }

    private Film mapFilm(ResultSet rs, int row) throws SQLException {
        return new Film(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("synopsis"),
                rs.getInt("duration_minutes"),
                rs.getString("genre"),
                rs.getString("poster_url"));
    }
}
