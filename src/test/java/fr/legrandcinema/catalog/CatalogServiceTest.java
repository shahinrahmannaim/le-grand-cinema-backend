package fr.legrandcinema.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

class CatalogServiceTest {
    private final CatalogRepository repository = mock(CatalogRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T23:30:00Z"), ZoneOffset.UTC);
    private final CatalogService service = new CatalogService(repository, clock);
    private final UUID filmId = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void missingOrUnpublishedFilmDoesNotExposeScreenings() {
        when(repository.findPublishedFilm(filmId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.screenings(filmId, null));
        verify(repository, never()).findScreenings(any(), any(), any());
    }

    @Test
    void defaultDayUsesParisRatherThanUtc() {
        when(repository.findPublishedFilm(filmId))
                .thenReturn(Optional.of(new Film(filmId, "Film", "Résumé", 90, "Drame", null)));
        when(repository.findScreenings(eq(filmId), any(), any())).thenReturn(List.of());
        assertEquals(List.of(), service.screenings(filmId, null));
        verify(repository)
                .findScreenings(
                        filmId,
                        DateWindow.forDate(LocalDate.of(2026, 11, 7), CatalogService.CINEMA_ZONE),
                        clock.instant());
    }

    @Test
    void filmCatalogueUsesInjectedTime() {
        when(repository.findPublishedFilms(clock.instant())).thenReturn(List.of());
        assertEquals(List.of(), service.films());
        verify(repository).findPublishedFilms(clock.instant());
    }
}
