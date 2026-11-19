package fr.legrandcinema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("demo")
@Transactional
class DatabaseIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void initialCinemaContainsTenRoomsAndSeventeenHundredSeats() {
        assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM room", Integer.class));
        assertEquals(1700, jdbc.queryForObject("SELECT count(*) FROM seat", Integer.class));
        assertEquals(1700, jdbc.queryForObject("SELECT sum(capacity) FROM room", Integer.class));
    }

    @Test
    void demoCatalogueContainsTenDistinctPublishedFilmsAndSevenNewScheduledTitles() {
        assertEquals(
                10,
                jdbc.queryForObject(
                        "SELECT count(*) FROM film WHERE published AND id BETWEEN"
                            + " '10000000-0000-0000-0000-000000000001'::uuid AND"
                            + " '10000000-0000-0000-0000-000000000010'::uuid",
                        Integer.class));
        assertEquals(
                7,
                jdbc.queryForObject(
                        "SELECT count(DISTINCT film_id) FROM screening WHERE NOT cancelled"
                            + " AND starts_at > now() AND film_id BETWEEN"
                            + " '10000000-0000-0000-0000-000000000004'::uuid AND"
                            + " '10000000-0000-0000-0000-000000000010'::uuid",
                        Integer.class));
    }

    @Test
    void overlappingScreeningsInTheSameRoomAreRejected() {
        UUID film = UUID.randomUUID();
        UUID room = jdbc.queryForObject("SELECT id FROM room ORDER BY name LIMIT 1", UUID.class);
        jdbc.update(
                "INSERT INTO film(id,title,synopsis,duration_minutes,genre) VALUES"
                    + " (?,?,?,90,'Test')",
                film,
                "Test",
                "Données synthétiques");
        Instant start = Instant.parse("2035-01-01T12:00:00Z");
        String sql =
                "INSERT INTO"
                    + " screening(film_id,room_id,starts_at,ends_at,occupied_until,price_cents)"
                    + " VALUES (?,?,?,?,?,1000)";
        jdbc.update(
                sql,
                film,
                room,
                Timestamp.from(start),
                Timestamp.from(start.plusSeconds(5400)),
                Timestamp.from(start.plusSeconds(7200)));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                sql,
                                film,
                                room,
                                Timestamp.from(start.plusSeconds(3600)),
                                Timestamp.from(start.plusSeconds(9000)),
                                Timestamp.from(start.plusSeconds(10800))));
    }

    @Test
    void aSeatFromAnotherRoomCannotBeAssignedToAScreening() {
        UUID film = UUID.randomUUID();
        UUID screening = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        UUID room = jdbc.queryForObject("SELECT id FROM room ORDER BY name LIMIT 1", UUID.class);
        UUID otherSeat =
                jdbc.queryForObject(
                        "SELECT id FROM seat WHERE room_id<>? LIMIT 1", UUID.class, room);
        jdbc.update(
                "INSERT INTO film(id,title,synopsis,duration_minutes,genre) VALUES (?,"
                    + " 'Test','Test',90,'Test')",
                film);
        jdbc.update(
                "INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES"
                    + " (?,'test@example.invalid','unused','Test','CLIENT')",
                actor);
        jdbc.update(
                """
                INSERT INTO screening(id,film_id,room_id,starts_at,ends_at,occupied_until,price_cents)
                VALUES (?,?,?,'2035-02-01T12:00:00Z','2035-02-01T13:30:00Z','2035-02-01T14:00:00Z',1000)
                """,
                screening,
                film,
                room);
        jdbc.update(
                """
                INSERT INTO reservation(id,reference,screening_id,customer_id,actor_id,channel,total_cents,idempotency_key,request_hash)
                VALUES (?,'TEST',?,?,?,'WEB',1000,?,?)
                """,
                reservation,
                screening,
                actor,
                actor,
                UUID.randomUUID(),
                "a".repeat(64));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO reservation_seat(reservation_id,screening_id,room_id,seat_id,unit_price_cents)
                                VALUES (?,?,?,?,1000)
                                """,
                                reservation,
                                screening,
                                room,
                                otherSeat));
    }
}
