package fr.legrandcinema.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DateWindowTest {
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    void springDayHasTwentyThreeHours() {
        DateWindow window = DateWindow.forDate(LocalDate.of(2026, 3, 29), PARIS);
        assertEquals(23, Duration.between(window.from(), window.until()).toHours());
    }

    @Test
    void autumnDayHasTwentyFiveHours() {
        DateWindow window = DateWindow.forDate(LocalDate.of(2026, 10, 25), PARIS);
        assertEquals(25, Duration.between(window.from(), window.until()).toHours());
    }
}
