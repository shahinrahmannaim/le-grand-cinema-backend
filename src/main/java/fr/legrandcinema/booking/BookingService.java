package fr.legrandcinema.booking;

import fr.legrandcinema.shared.ApiException;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {
    public record Request(
            @NotNull UUID screeningId,
            @NotEmpty @Size(max = 10) List<@NotNull UUID> seatIds,
            @NotNull UUID idempotencyKey) {}

    public record Seat(UUID id, int rowNumber, int seatNumber, boolean occupied) {}

    public record Plan(
            UUID screeningId,
            String title,
            String roomName,
            Instant startsAt,
            int priceCents,
            List<Seat> seats) {}

    public record Ticket(
            UUID id,
            String reference,
            String title,
            String roomName,
            Instant startsAt,
            String status,
            String channel,
            int totalCents,
            List<String> seats) {}

    private record Show(UUID room, int price, Instant starts, boolean available) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SeatEvents events;

    public BookingService(
            JdbcTemplate jdbc, TransactionTemplate tx, Clock clock, SeatEvents events) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.events = events;
    }

    public Plan plan(UUID id) {
        var details =
                jdbc.query(
                        """
                        SELECT s.*,f.title,r.name FROM screening s JOIN film f ON f.id=s.film_id JOIN room r ON r.id=s.room_id
                        WHERE s.id=? AND NOT s.cancelled AND f.published AND s.starts_at>?
                        """,
                        (rs, n) ->
                                new Plan(
                                        id,
                                        rs.getString("title"),
                                        rs.getString("name"),
                                        rs.getTimestamp("starts_at").toInstant(),
                                        rs.getInt("price_cents"),
                                        List.of()),
                        id,
                        Timestamp.from(clock.instant()));
        Plan p = details.stream().findFirst().orElseThrow();
        List<Seat> seats =
                jdbc.query(
                        """
                        SELECT t.*,EXISTS(SELECT 1 FROM reservation_seat b WHERE b.screening_id=s.id AND b.seat_id=t.id AND b.released_at IS NULL) AS occupied
                        FROM screening s JOIN seat t ON t.room_id=s.room_id WHERE s.id=? ORDER BY t.row_number,t.seat_number
                        """,
                        (rs, n) ->
                                new Seat(
                                        rs.getObject("id", UUID.class),
                                        rs.getInt("row_number"),
                                        rs.getInt("seat_number"),
                                        rs.getBoolean("occupied")),
                        id);
        return new Plan(id, p.title(), p.roomName(), p.startsAt(), p.priceCents(), seats);
    }

    public Ticket confirm(UUID actor, Request request, boolean boxOffice) {
        return tx.execute(
                status -> {
                    // Serialize retries for one actor before inspecting idempotency. Unique seat
                    // index remains final authority.
                    jdbc.queryForObject(
                            "SELECT id FROM app_user WHERE id=? FOR UPDATE", UUID.class, actor);
                    String hash = hash(request, boxOffice);
                    List<Map<String, Object>> previous =
                            jdbc.queryForList(
                                    "SELECT id,request_hash FROM reservation WHERE actor_id=? AND"
                                        + " idempotency_key=?",
                                    actor,
                                    request.idempotencyKey());
                    if (!previous.isEmpty()) {
                        if (!hash.equals(previous.getFirst().get("request_hash")))
                            throw new ApiException(
                                    409,
                                    "Clé de confirmation déjà utilisée pour une autre demande.");
                        return ticket((UUID) previous.getFirst().get("id"), actor, boxOffice);
                    }
                    List<UUID> seats = request.seatIds().stream().distinct().sorted().toList();
                    if (seats.size() != request.seatIds().size())
                        throw new ApiException(
                                400, "Un siège figure plusieurs fois dans la sélection.");
                    Show show =
                            jdbc
                                    .query(
                                            """
                                            SELECT s.*,f.published FROM screening s JOIN film f ON f.id=s.film_id WHERE s.id=? FOR SHARE OF s
                                            """,
                                            (rs, n) ->
                                                    new Show(
                                                            rs.getObject("room_id", UUID.class),
                                                            rs.getInt("price_cents"),
                                                            rs.getTimestamp("starts_at")
                                                                    .toInstant(),
                                                            !rs.getBoolean("cancelled")
                                                                    && rs.getBoolean("published")),
                                            request.screeningId())
                                    .stream()
                                    .findFirst()
                                    .orElseThrow();
                    if (!show.available() || !show.starts().isAfter(clock.instant()))
                        throw new ApiException(409, "Cette séance n’est plus réservable.");
                    for (UUID seat : seats) {
                        Integer count =
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM seat WHERE id=? AND room_id=?",
                                        Integer.class,
                                        seat,
                                        show.room());
                        if (count == null || count != 1)
                            throw new ApiException(400, "Siège étranger à cette salle.");
                    }
                    UUID id = UUID.randomUUID();
                    String reference =
                            "GC-" + id.toString().replace("-", "").substring(0, 24).toUpperCase();
                    jdbc.update(
                            """
                            INSERT INTO reservation(id,reference,screening_id,customer_id,actor_id,channel,total_cents,idempotency_key,request_hash)
                            VALUES(?,?,?,?,?,?,?,?,?)
                            """,
                            id,
                            reference,
                            request.screeningId(),
                            boxOffice ? null : actor,
                            actor,
                            boxOffice ? "BOX_OFFICE" : "WEB",
                            Math.multiplyExact(show.price(), seats.size()),
                            request.idempotencyKey(),
                            hash);
                    for (UUID seat : seats)
                        jdbc.update(
                                """
                                INSERT INTO reservation_seat(reservation_id,screening_id,room_id,seat_id,unit_price_cents) VALUES(?,?,?,?,?)
                                """,
                                id,
                                request.screeningId(),
                                show.room(),
                                seat,
                                show.price());
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    events.changed(request.screeningId());
                                }
                            });
                    return ticket(id, actor, boxOffice);
                });
    }

    public Ticket ticket(UUID id, UUID actor, boolean staff) {
        List<Ticket> tickets =
                jdbc.query(
                        """
                        SELECT b.*,f.title,r.name,s.starts_at FROM reservation b JOIN screening s ON s.id=b.screening_id
                        JOIN film f ON f.id=s.film_id JOIN room r ON r.id=s.room_id
                        WHERE b.id=? AND (? OR b.customer_id=?)
                        """,
                        (rs, n) ->
                                new Ticket(
                                        id,
                                        rs.getString("reference"),
                                        rs.getString("title"),
                                        rs.getString("name"),
                                        rs.getTimestamp("starts_at").toInstant(),
                                        rs.getString("status"),
                                        rs.getString("channel"),
                                        rs.getInt("total_cents"),
                                        List.of()),
                        id,
                        staff,
                        actor);
        Ticket b = tickets.stream().findFirst().orElseThrow();
        List<String> seats =
                jdbc.query(
                        """
                        SELECT s.row_number,s.seat_number FROM reservation_seat b JOIN seat s ON s.id=b.seat_id
                        WHERE b.reservation_id=? AND b.released_at IS NULL ORDER BY s.row_number,s.seat_number
                        """,
                        (rs, n) -> "R" + rs.getInt("row_number") + " · " + rs.getInt("seat_number"),
                        id);
        return new Ticket(
                id,
                b.reference(),
                b.title(),
                b.roomName(),
                b.startsAt(),
                b.status(),
                b.channel(),
                b.totalCents(),
                seats);
    }

    public List<Ticket> mine(UUID actor) {
        return jdbc
                .query(
                        "SELECT id FROM reservation WHERE customer_id=? ORDER BY created_at DESC"
                            + " LIMIT 100",
                        (rs, n) -> rs.getObject(1, UUID.class),
                        actor)
                .stream()
                .map(id -> ticket(id, actor, false))
                .toList();
    }

    static String hash(Request r, boolean box) {
        try {
            String canonical =
                    r.screeningId()
                            + "|"
                            + box
                            + "|"
                            + r.seatIds().stream()
                                    .sorted()
                                    .map(UUID::toString)
                                    .reduce("", (a, b) -> a + "," + b);
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
