package fr.legrandcinema.catalog;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CatalogService {
    static final ZoneId CINEMA_ZONE = ZoneId.of("Europe/Paris");
    private final CatalogRepository repository;
    private final Clock clock;

    public CatalogService(CatalogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<Film> films() {
        return repository.findPublishedFilms(clock.instant());
    }

    public List<Screening> screenings(UUID filmId, LocalDate date) {
        repository.findPublishedFilm(filmId).orElseThrow(NoSuchElementException::new);
        LocalDate day = date == null ? LocalDate.now(clock.withZone(CINEMA_ZONE)) : date;
        return repository.findScreenings(
                filmId, DateWindow.forDate(day, CINEMA_ZONE), clock.instant());
    }
}
