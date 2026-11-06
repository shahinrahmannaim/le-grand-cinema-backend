package fr.legrandcinema.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** A cinema day follows local midnight boundaries, including daylight-saving transitions. */
public record DateWindow(Instant from, Instant until) {
    public static DateWindow forDate(LocalDate date, ZoneId zone) {
        return new DateWindow(
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
