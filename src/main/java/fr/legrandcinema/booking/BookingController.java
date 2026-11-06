package fr.legrandcinema.booking;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
public class BookingController {
    private final BookingService bookings;
    private final SeatEvents events;

    public BookingController(BookingService bookings, SeatEvents events) {
        this.bookings = bookings;
        this.events = events;
    }

    @GetMapping("/api/catalog/screenings/{id}/seats")
    BookingService.Plan plan(@PathVariable UUID id, HttpServletResponse response) {
        noCache(response);
        return bookings.plan(id);
    }

    @GetMapping(
            value = "/api/catalog/screenings/{id}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID id, HttpServletResponse response) {
        bookings.plan(id);
        noCache(response);
        response.setHeader("X-Accel-Buffering", "no");
        return events.connect(id);
    }

    @PostMapping("/api/reservations")
    BookingService.Ticket confirm(
            @Valid @RequestBody BookingService.Request request, Authentication auth) {
        return bookings.confirm(UUID.fromString(auth.getName()), request, false);
    }

    @PostMapping("/api/staff/reservations")
    BookingService.Ticket counter(
            @Valid @RequestBody BookingService.Request request, Authentication auth) {
        return bookings.confirm(UUID.fromString(auth.getName()), request, true);
    }

    @GetMapping("/api/reservations")
    List<BookingService.Ticket> mine(Authentication auth, HttpServletResponse response) {
        noCache(response);
        return bookings.mine(UUID.fromString(auth.getName()));
    }

    @GetMapping("/api/reservations/{id}")
    BookingService.Ticket ticket(
            @PathVariable UUID id, Authentication auth, HttpServletResponse response) {
        noCache(response);
        boolean staff =
                auth.getAuthorities().stream()
                        .anyMatch(
                                a ->
                                        a.getAuthority().equals("ROLE_ADMIN")
                                                || a.getAuthority().equals("ROLE_STAFF"));
        return bookings.ticket(id, UUID.fromString(auth.getName()), staff);
    }

    private static void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
}
