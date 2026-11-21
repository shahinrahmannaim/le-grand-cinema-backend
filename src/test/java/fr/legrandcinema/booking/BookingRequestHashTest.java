package fr.legrandcinema.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class BookingRequestHashTest {
    private final UUID screening = UUID.randomUUID();
    private final UUID firstSeat = UUID.randomUUID();
    private final UUID secondSeat = UUID.randomUUID();

    @Test
    void retryFingerprintIgnoresSeatOrderingAndTheKeyItself() {
        var first =
                new BookingService.Request(
                        screening, List.of(firstSeat, secondSeat), UUID.randomUUID());
        var retry =
                new BookingService.Request(
                        screening, List.of(secondSeat, firstSeat), UUID.randomUUID());
        assertEquals(BookingService.hash(first, false), BookingService.hash(retry, false));
    }

    @Test
    void changedScreeningOrSeatsProducesADifferentFingerprint() {
        var original = new BookingService.Request(screening, List.of(firstSeat), UUID.randomUUID());
        var otherScreening =
                new BookingService.Request(
                        UUID.randomUUID(), original.seatIds(), original.idempotencyKey());
        var otherSeat =
                new BookingService.Request(
                        screening, List.of(secondSeat), original.idempotencyKey());
        assertNotEquals(
                BookingService.hash(original, false), BookingService.hash(otherScreening, false));
        assertNotEquals(
                BookingService.hash(original, false), BookingService.hash(otherSeat, false));
    }

    @Test
    void boxOfficeAndWebRequestsCannotReuseTheSameFingerprint() {
        var request = new BookingService.Request(screening, List.of(firstSeat), UUID.randomUUID());
        assertNotEquals(BookingService.hash(request, false), BookingService.hash(request, true));
    }
}
