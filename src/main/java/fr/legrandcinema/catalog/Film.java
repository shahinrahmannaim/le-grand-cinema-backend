package fr.legrandcinema.catalog;

import java.util.UUID;

public record Film(UUID id, String title, String synopsis, int durationMinutes, String genre, String posterUrl) {
}
