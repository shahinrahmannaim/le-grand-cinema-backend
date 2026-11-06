package fr.legrandcinema.catalog;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class CatalogService {
    private final CatalogRepository repository;
    private final Clock clock;
    public CatalogService(CatalogRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    public List<Film> films() { return repository.findPublishedFilms(clock.instant()); }
}
