package fr.legrandcinema.admin;

import fr.legrandcinema.shared.ApiException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private final JdbcTemplate jdbc;

    public StaffController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/reservations")
    Map<String, Object> search(
            @RequestParam(defaultValue = "") String reference,
            @RequestParam(required = false) UUID screeningId,
            @RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 100000 || reference.length() > 32)
            throw new ApiException(400, "Recherche invalide.");
        String ref = reference.trim().toUpperCase(Locale.ROOT);
        Long total =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM reservation b
                        WHERE (?='' OR position(? in b.reference)>0)
                          AND (CAST(? AS UUID) IS NULL OR b.screening_id=CAST(? AS UUID))
                        """,
                        Long.class,
                        ref,
                        ref,
                        screeningId,
                        screeningId);
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT b.id,b.reference,b.status,b.channel,b.total_cents,b.created_at,s.starts_at,f.title,r.name AS room_name,
                        (SELECT count(*) FROM reservation_seat z WHERE z.reservation_id=b.id AND z.released_at IS NULL) AS seats
                        FROM reservation b JOIN screening s ON s.id=b.screening_id JOIN film f ON f.id=s.film_id JOIN room r ON r.id=s.room_id
                        WHERE (?='' OR position(? in b.reference)>0)
                          AND (CAST(? AS UUID) IS NULL OR b.screening_id=CAST(? AS UUID))
                        ORDER BY b.created_at DESC,b.id LIMIT 25 OFFSET ?
                        """,
                        ref,
                        ref,
                        screeningId,
                        screeningId,
                        page * 25);
        return Map.of(
                "items", rows, "page", page, "pageSize", 25, "total", total == null ? 0 : total);
    }

    @GetMapping("/occupancy")
    List<Map<String, Object>> occupancy(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "0") int threshold) {
        validate(from, to);
        if (threshold < 0 || threshold > 100)
            throw new ApiException(400, "Seuil attendu entre 0 et 100.");
        return jdbc.queryForList(
                """
                SELECT s.id,s.starts_at,f.title,r.name AS room_name,r.capacity,count(b.id) AS booked_seats,
                  round(100.0*count(b.id)/r.capacity,2) AS occupancy_percent
                FROM screening s JOIN film f ON f.id=s.film_id JOIN room r ON r.id=s.room_id
                LEFT JOIN reservation_seat b ON b.screening_id=s.id AND b.released_at IS NULL
                WHERE NOT s.cancelled AND s.starts_at>=? AND s.starts_at<?
                GROUP BY s.id,f.title,r.id HAVING 100.0*count(b.id)/r.capacity>=? ORDER BY s.starts_at,s.id
                """,
                start(from),
                start(to.plusDays(1)),
                threshold);
    }

    @GetMapping("/analytics")
    Map<String, Object> analytics(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) LocalDate baselineFrom,
            @RequestParam(required = false) LocalDate baselineTo) {
        validate(from, to);
        if ((baselineFrom == null) != (baselineTo == null))
            throw new ApiException(400, "Les deux dates de comparaison sont nécessaires.");
        if (baselineFrom != null) {
            validate(baselineFrom, baselineTo);
            if (ChronoUnit.DAYS.between(from, to)
                    != ChronoUnit.DAYS.between(baselineFrom, baselineTo))
                throw new ApiException(400, "Comparez deux périodes de même durée.");
            if (!baselineTo.isBefore(from) && !baselineFrom.isAfter(to))
                throw new ApiException(400, "Les périodes de comparaison doivent être distinctes.");
        }
        return Map.of(
                "from",
                from,
                "to",
                to,
                "current",
                aggregate(from, to),
                "baseline",
                baselineFrom == null ? List.of() : aggregate(baselineFrom, baselineTo),
                "limitation",
                "Places réservées, pas entrées constatées. Zéro réservation ne prouve pas une"
                        + " absence de données historiques. Aucune prédiction de fréquentation.");
    }

    private List<Map<String, Object>> aggregate(LocalDate from, LocalDate to) {
        return jdbc.queryForList(
                """
                SELECT (s.starts_at AT TIME ZONE 'Europe/Paris')::date::text AS day,
                    extract(hour from s.starts_at AT TIME ZONE 'Europe/Paris')::int AS hour,
                    extract(isodow from s.starts_at AT TIME ZONE 'Europe/Paris') IN (6,7) AS weekend,
                    count(DISTINCT s.id) AS screenings,count(b.id) AS booked_seats
                FROM screening s LEFT JOIN reservation_seat b ON b.screening_id=s.id AND b.released_at IS NULL
                WHERE NOT s.cancelled AND s.starts_at>=? AND s.starts_at<? GROUP BY 1,2,3 ORDER BY 1,2
                """,
                start(from),
                start(to.plusDays(1)));
    }

    private static Timestamp start(LocalDate date) {
        return Timestamp.from(date.atStartOfDay(PARIS).toInstant());
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365)
            throw new ApiException(400, "Période attendue entre 1 et 366 jours.");
    }
}
