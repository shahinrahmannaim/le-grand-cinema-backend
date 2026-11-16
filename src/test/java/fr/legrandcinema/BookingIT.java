package fr.legrandcinema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.legrandcinema.booking.BookingService;
import fr.legrandcinema.shared.ApiException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("demo")
class BookingIT {
    @Autowired BookingService service;
    @Autowired JdbcTemplate jdbc;
    UUID show, room, actor, other;
    List<UUID> seats;
    List<UUID> actorsCreated = new ArrayList<>();

    @BeforeEach
    void fixture() {
        room = UUID.randomUUID();
        UUID film = UUID.randomUUID();
        show = UUID.randomUUID();
        actor = user();
        other = user();
        jdbc.update(
                "INSERT INTO room(id,name,category,capacity) VALUES(?,?,'STANDARD',3)",
                room,
                "Test " + room);
        jdbc.update(
                "INSERT INTO film(id,title,synopsis,duration_minutes,genre,published)"
                    + " VALUES(?,'Test','Synopsis',90,'Test',true)",
                film);
        Instant start = Instant.now().plusSeconds(86400 * 10);
        jdbc.update(
                "INSERT INTO"
                    + " screening(id,film_id,room_id,starts_at,ends_at,occupied_until,price_cents)"
                    + " VALUES(?,?,?,?,?,?,1100)",
                show,
                film,
                room,
                Timestamp.from(start),
                Timestamp.from(start.plusSeconds(5400)),
                Timestamp.from(start.plusSeconds(7200)));
        seats = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            UUID seat = UUID.randomUUID();
            seats.add(seat);
            jdbc.update(
                    "INSERT INTO seat(id,room_id,row_number,seat_number) VALUES(?,?,1,?)",
                    seat,
                    room,
                    i);
        }
    }

    UUID user() {
        UUID id = UUID.randomUUID();
        actorsCreated.add(id);
        jdbc.update(
                "INSERT INTO app_user(id,email,password_hash,display_name,role)"
                    + " VALUES(?,?,'not-a-login-hash','Test','CLIENT')",
                id,
                id + "@test.example");
        return id;
    }

    @AfterEach
    void cleanup() {
        UUID film =
                jdbc.queryForObject("SELECT film_id FROM screening WHERE id=?", UUID.class, show);
        jdbc.update("DELETE FROM reservation_seat WHERE screening_id=?", show);
        jdbc.update("DELETE FROM reservation WHERE screening_id=?", show);
        jdbc.update("DELETE FROM screening WHERE id=?", show);
        jdbc.update("DELETE FROM film WHERE id=?", film);
        jdbc.update("DELETE FROM seat WHERE room_id=?", room);
        jdbc.update("DELETE FROM room WHERE id=?", room);
        for (UUID id : actorsCreated) jdbc.update("DELETE FROM app_user WHERE id=?", id);
    }

    BookingService.Request request(List<UUID> selected) {
        return new BookingService.Request(show, selected, UUID.randomUUID());
    }

    @Test
    void twentyConcurrentClientsYieldExactlyOneBooking() throws Exception {
        List<UUID> actors = new ArrayList<>();
        for (int i = 0; i < 20; i++) actors.add(user());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(20)) {
            for (UUID who : actors)
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        service.confirm(
                                                who, request(List.of(seats.getFirst())), false);
                                        return true;
                                    } catch (DataIntegrityViolationException e) {
                                        return false;
                                    }
                                }));
            start.countDown();
            int won = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) won++;
            assertEquals(1, won);
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM reservation WHERE screening_id=?",
                        Integer.class,
                        show));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM reservation_seat WHERE screening_id=?",
                        Integer.class,
                        show));
    }

    @Test
    void conflictRollsBackEverySeatAndReservation() {
        service.confirm(other, request(List.of(seats.get(1))), false);
        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.confirm(actor, request(List.of(seats.get(0), seats.get(1))), false));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM reservation WHERE screening_id=?",
                        Integer.class,
                        show));
        assertTrue(
                service.plan(show).seats().stream()
                        .filter(s -> s.id().equals(seats.getFirst()))
                        .noneMatch(BookingService.Seat::occupied));
    }

    @Test
    void retriesReturnSameTicketEvenWhenSeatAlreadyOccupied() {
        var r = request(List.of(seats.getFirst(), seats.get(1)));
        var first = service.confirm(actor, r, false);
        var second =
                service.confirm(
                        actor,
                        new BookingService.Request(
                                show, List.of(seats.get(1), seats.getFirst()), r.idempotencyKey()),
                        false);
        assertEquals(first.id(), second.id());
        assertEquals(2200, first.totalCents());
        assertEquals(2, first.seats().size());
        assertThrows(
                ApiException.class,
                () ->
                        service.confirm(
                                actor,
                                new BookingService.Request(
                                        show, List.of(seats.get(2)), r.idempotencyKey()),
                                false));
    }

    @Test
    void idempotencyIsScopedToActor() {
        UUID key = UUID.randomUUID();
        var a =
                service.confirm(
                        actor,
                        new BookingService.Request(show, List.of(seats.getFirst()), key),
                        false);
        var b =
                service.confirm(
                        other, new BookingService.Request(show, List.of(seats.get(1)), key), false);
        assertTrue(!a.id().equals(b.id()));
    }

    @Test
    void ownerAndStaffCanReadTicketButOtherClientCannot() {
        var ticket = service.confirm(actor, request(List.of(seats.getFirst())), false);
        assertEquals(ticket.id(), service.ticket(ticket.id(), actor, false).id());
        assertThrows(NoSuchElementException.class, () -> service.ticket(ticket.id(), other, false));
        assertEquals(ticket.id(), service.ticket(ticket.id(), other, true).id());
        assertEquals(1, service.mine(actor).size());
        assertEquals(0, service.mine(other).size());
    }

    @Test
    void boxOfficeUsesSameInventoryAndIsReadableByStaff() {
        var ticket = service.confirm(actor, request(List.of(seats.getFirst())), true);
        assertEquals("BOX_OFFICE", ticket.channel());
        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.confirm(other, request(List.of(seats.getFirst())), false));
    }

    @Test
    void wrongRoomAndDuplicateSeatsAreRejectedWithoutWriting() {
        UUID foreign =
                jdbc.queryForObject(
                        "SELECT id FROM seat WHERE room_id<>? LIMIT 1", UUID.class, room);
        assertThrows(
                ApiException.class, () -> service.confirm(actor, request(List.of(foreign)), false));
        assertThrows(
                ApiException.class,
                () ->
                        service.confirm(
                                actor,
                                request(List.of(seats.getFirst(), seats.getFirst())),
                                false));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM reservation WHERE screening_id=?",
                        Integer.class,
                        show));
    }

    @Test
    void cancelledAndPastScreeningsCannotBeBooked() {
        jdbc.update("UPDATE screening SET cancelled=true WHERE id=?", show);
        assertThrows(
                ApiException.class,
                () -> service.confirm(actor, request(List.of(seats.getFirst())), false));
        jdbc.update(
                "UPDATE screening SET cancelled=false,starts_at=starts_at-interval '20"
                    + " days',ends_at=ends_at-interval '20"
                    + " days',occupied_until=occupied_until-interval '20 days' WHERE id=?",
                show);
        assertThrows(
                ApiException.class,
                () -> service.confirm(actor, request(List.of(seats.getFirst())), false));
    }
}
