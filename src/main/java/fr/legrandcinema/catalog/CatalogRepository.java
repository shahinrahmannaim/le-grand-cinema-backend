package fr.legrandcinema.catalog;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    public CatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Film> findPublishedFilms(Instant now) {
        return jdbc.query("""
            SELECT f.* FROM film f WHERE f.published AND EXISTS (
              SELECT 1 FROM screening s WHERE s.film_id=f.id AND NOT s.cancelled AND s.starts_at > ?
            ) ORDER BY lower(f.title), f.id
            """, this::mapFilm, Timestamp.from(now));
    }
    private Film mapFilm(ResultSet rs, int row) throws SQLException {
        return new Film(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("synopsis"),
            rs.getInt("duration_minutes"), rs.getString("genre"), rs.getString("poster_url"));
    }
}
