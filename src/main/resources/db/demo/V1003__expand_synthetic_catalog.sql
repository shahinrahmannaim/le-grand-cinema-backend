-- Seven additional fictional demo films. Existing records and bookings are preserved.
INSERT INTO film(id,title,synopsis,duration_minutes,genre,published) VALUES
('10000000-0000-0000-0000-000000000004','Le jardin des possibles','Des voisins transforment une cour abandonnée en jardin partagé.',100,'Comédie',true),
('10000000-0000-0000-0000-000000000005','Dernier train pour demain','Deux inconnus suivent les indices d’un carnet oublié dans un train.',110,'Mystère',true),
('10000000-0000-0000-0000-000000000006','La forêt des nuages','Une cartographe explore une forêt où les nuages dessinent les chemins.',90,'Animation',true),
('10000000-0000-0000-0000-000000000007','Les voix du quartier','Des habitants de toutes générations préparent ensemble un concert.',105,'Drame musical',true),
('10000000-0000-0000-0000-000000000008','Au-delà du phare','Une navigatrice retrouve les traces d’une expédition oubliée.',115,'Aventure',true),
('10000000-0000-0000-0000-000000000009','Un été à bicyclette','Trois amis traversent la campagne pour tenir une promesse d’enfance.',95,'Comédie',true),
('10000000-0000-0000-0000-000000000010','La mémoire des pierres','Une archéologue découvre l’histoire cachée de son village.',120,'Drame',true)
ON CONFLICT (id) DO NOTHING;

-- V1001 uses the first three rooms in this same order. Additional films use rooms 4–10.
-- Three slots daily for 30 days, anchored on the Paris calendar at migration time.
WITH extra_films AS (
    SELECT id, duration_minutes, row_number() OVER (ORDER BY id) + 3 AS n
    FROM film
    WHERE id BETWEEN '10000000-0000-0000-0000-000000000004'::uuid
                 AND '10000000-0000-0000-0000-000000000010'::uuid
), rooms AS (
    SELECT id, turnaround_minutes, row_number() OVER (ORDER BY name) AS n FROM room
), candidates AS (
    SELECT f.id AS film_id, r.id AS room_id, t.start_time,
           t.start_time + make_interval(mins => f.duration_minutes) AS end_time,
           t.start_time + make_interval(mins => f.duration_minutes + r.turnaround_minutes) AS busy_until
    FROM extra_films f JOIN rooms r ON r.n=f.n
    CROSS JOIN generate_series(0, 29) AS day_offset
    CROSS JOIN (VALUES (10),(14),(18)) AS slot(hour)
    CROSS JOIN LATERAL (
        SELECT ((((now() AT TIME ZONE 'Europe/Paris')::date + day_offset)::timestamp
                 + make_interval(hours => slot.hour)) AT TIME ZONE 'Europe/Paris') AS start_time
    ) t
)
INSERT INTO screening(film_id,room_id,starts_at,ends_at,occupied_until,price_cents)
SELECT c.film_id, c.room_id, c.start_time, c.end_time, c.busy_until, 1100
FROM candidates c
WHERE c.start_time > now()
  AND NOT EXISTS (
    SELECT 1 FROM screening s
    WHERE s.room_id=c.room_id AND NOT s.cancelled
      AND tstzrange(s.starts_at,s.occupied_until,'[)')
          && tstzrange(c.start_time,c.busy_until,'[)')
  )
ON CONFLICT DO NOTHING;
