package fr.legrandcinema.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.legrandcinema.booking.BookingService;
import fr.legrandcinema.shared.ApiException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("demo")
@Transactional
class StatisticsIT {
    @Autowired StaffController staff;
    @Autowired BookingService bookings;
    @Autowired JdbcTemplate jdbc;
    UUID show, actor;
    LocalDate day;
    List<UUID> seats;

    @BeforeEach
    void fixture() {
        UUID room = UUID.randomUUID(), film = UUID.randomUUID();
        show = UUID.randomUUID();
        actor = UUID.randomUUID();
        seats = List.of(UUID.randomUUID(), UUID.randomUUID());
        day =
                LocalDate.now(ZoneId.of("Europe/Paris"))
                        .plusWeeks(10)
                        .with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        var start = day.atTime(18, 0).atZone(ZoneId.of("Europe/Paris")).toInstant();
        jdbc.update(
                "INSERT INTO app_user(id,email,password_hash,display_name,role)"
                    + " VALUES(?,?,'unused','Test','CLIENT')",
                actor,
                actor + "@test.example");
        jdbc.update(
                "INSERT INTO room(id,name,category,capacity) VALUES(?,?,'STANDARD',10)",
                room,
                "Stats " + room);
        jdbc.update(
                "INSERT INTO film(id,title,synopsis,duration_minutes,genre,published)"
                    + " VALUES(?,'Stats','Synopsis',90,'Test',true)",
                film);
        jdbc.update(
                "INSERT INTO"
                    + " screening(id,film_id,room_id,starts_at,ends_at,occupied_until,price_cents)"
                    + " VALUES(?,?,?,?,?,?,1000)",
                show,
                film,
                room,
                Timestamp.from(start),
                Timestamp.from(start.plusSeconds(5400)),
                Timestamp.from(start.plusSeconds(7200)));
        for (int n = 0; n < seats.size(); n++)
            jdbc.update(
                    "INSERT INTO seat(id,room_id,row_number,seat_number) VALUES(?,?,1,?)",
                    seats.get(n),
                    room,
                    n + 1);
    }

    private Map<String, Object> row(int threshold) {
        return staff.occupancy(day, day, threshold).stream()
                .filter(r -> show.equals(r.get("id")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void occupancyUsesBookedSeatsOverCapacityIncludingAnEmptyScreening() {
        assertEquals(0, ((Number) row(0).get("booked_seats")).intValue());
        bookings.confirm(actor, new BookingService.Request(show, seats, UUID.randomUUID()), false);
        assertEquals(2, ((Number) row(0).get("booked_seats")).intValue());
        assertEquals(10, ((Number) row(0).get("capacity")).intValue());
        assertEquals(20.0, ((Number) row(0).get("occupancy_percent")).doubleValue());
    }

    @Test
    void comparisonKeepsParisCalendarDateAndRejectsIncomparablePeriods() {
        bookings.confirm(actor, new BookingService.Request(show, seats, UUID.randomUUID()), false);
        var report = staff.analytics(day, day, day.minusDays(7), day.minusDays(7));
        assertTrue(report.get("current").toString().contains(day.toString()));
        assertTrue(report.get("current").toString().contains("weekend=true"));
        assertThrows(
                ApiException.class,
                () -> staff.analytics(day, day, day.minusDays(7), day.minusDays(6)));
        assertThrows(ApiException.class, () -> staff.analytics(day, day.plusDays(366), null, null));
        assertThrows(ApiException.class, () -> staff.analytics(day, day.minusDays(1), null, null));
    }


}
