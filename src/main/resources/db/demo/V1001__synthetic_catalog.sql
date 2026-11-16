-- Fictional titles; not real cinema programming. Dates anchored on the first demo migration.
INSERT INTO film(id,title,synopsis,duration_minutes,genre,published) VALUES
('10000000-0000-0000-0000-000000000001','Une nuit en musique',
 'Pendant un festival, une pianiste retrouve la ville où elle a grandi. Une rencontre change sa dernière nuit de concert.',105,'Drame musical',true),
('10000000-0000-0000-0000-000000000002','Les lumières du rivage',
 'Un projectionniste et sa petite-fille partent sauver le dernier cinéma de leur village côtier.',115,'Aventure',true),
('10000000-0000-0000-0000-000000000003','Le voyage des étoiles',
 'Une jeune astronome transforme une panne de courant en une nuit de découvertes pour tout son quartier.',95,'Animation',true);

INSERT INTO screening(film_id,room_id,starts_at,ends_at,occupied_until,price_cents)
SELECT f.id, r.id, t.start_time,
       t.start_time + make_interval(mins => f.duration_minutes),
       t.start_time + make_interval(mins => f.duration_minutes + r.turnaround_minutes), 1100
FROM (SELECT id, row_number() OVER (ORDER BY id) AS n, duration_minutes FROM film WHERE published) f
JOIN (SELECT id, row_number() OVER (ORDER BY name) AS n, turnaround_minutes FROM room) r ON r.n=f.n
CROSS JOIN generate_series(0, 29) AS day_offset
CROSS JOIN (VALUES (10),(14),(18)) AS slot(hour)
CROSS JOIN LATERAL (SELECT (((CURRENT_DATE + day_offset)::timestamp + make_interval(hours => slot.hour))
                   AT TIME ZONE 'Europe/Paris') AS start_time) t;
