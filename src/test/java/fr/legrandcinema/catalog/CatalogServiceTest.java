package fr.legrandcinema.catalog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
class CatalogServiceTest {
    @Test
    void filmCatalogueUsesInjectedTime() {
        CatalogRepository repository = mock(CatalogRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T23:30:00Z"), ZoneOffset.UTC);
        when(repository.findPublishedFilms(clock.instant())).thenReturn(List.of());
        assertEquals(List.of(), new CatalogService(repository, clock).films());
        verify(repository).findPublishedFilms(clock.instant());
    }
}
