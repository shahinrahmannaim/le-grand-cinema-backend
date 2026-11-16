package fr.legrandcinema.catalog;

import java.time.Instant;
import java.util.UUID;

public record Screening(UUID id, UUID filmId, String roomName, Instant startsAt, int priceCents) {}
