INSERT INTO room(name, category, capacity)
VALUES ('Premium 1','PREMIUM',300), ('Premium 2','PREMIUM',300),
       ('Standard 1','STANDARD',180), ('Standard 2','STANDARD',180),
       ('Standard 3','STANDARD',180), ('Standard 4','STANDARD',180),
       ('VIP 1','VIP',120), ('VIP 2','VIP',120),
       ('Événement 1','EVENT',70), ('Événement 2','EVENT',70);

-- Proposed numbering: ten seats per row. Confirm physical layouts with the cinema.
INSERT INTO seat(room_id, row_number, seat_number)
SELECT room.id, ((n - 1) / 10) + 1, ((n - 1) % 10) + 1
FROM room CROSS JOIN LATERAL generate_series(1, room.capacity) AS n;
