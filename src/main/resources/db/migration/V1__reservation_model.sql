CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CLIENT', 'STAFF', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_email_trimmed CHECK (email = lower(trim(email)))
);
CREATE UNIQUE INDEX user_email_unique ON app_user(email);

CREATE TABLE room (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(60) NOT NULL UNIQUE,
    category VARCHAR(20) NOT NULL CHECK (category IN ('PREMIUM', 'STANDARD', 'VIP', 'EVENT')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    turnaround_minutes INTEGER NOT NULL DEFAULT 30 CHECK (turnaround_minutes >= 0)
);
CREATE TABLE seat (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES room(id),
    row_number INTEGER NOT NULL CHECK (row_number > 0),
    seat_number INTEGER NOT NULL CHECK (seat_number > 0),
    UNIQUE(room_id, row_number, seat_number),
    UNIQUE(id, room_id)
);
CREATE TABLE film (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    synopsis TEXT NOT NULL,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 1 AND 600),
    genre VARCHAR(60) NOT NULL,
    poster_url VARCHAR(500),
    published BOOLEAN NOT NULL DEFAULT false
);
CREATE TABLE screening (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    film_id UUID NOT NULL REFERENCES film(id),
    room_id UUID NOT NULL REFERENCES room(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    occupied_until TIMESTAMPTZ NOT NULL,
    price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
    cancelled BOOLEAN NOT NULL DEFAULT false,
    CHECK (starts_at < ends_at AND ends_at <= occupied_until),
    UNIQUE(id, room_id),
    EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(starts_at, occupied_until, '[)') WITH &&
    ) WHERE (NOT cancelled)
);
CREATE INDEX screening_film_time ON screening(film_id, starts_at) WHERE NOT cancelled;

CREATE TABLE reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(32) NOT NULL UNIQUE,
    screening_id UUID NOT NULL REFERENCES screening(id),
    customer_id UUID REFERENCES app_user(id),
    actor_id UUID NOT NULL REFERENCES app_user(id),
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('WEB', 'BOX_OFFICE')),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    total_cents INTEGER NOT NULL CHECK (total_cents >= 0),
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (channel <> 'WEB' OR customer_id IS NOT NULL),
    UNIQUE(actor_id, idempotency_key),
    UNIQUE(id, screening_id)
);
CREATE TABLE reservation_seat (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL,
    screening_id UUID NOT NULL,
    room_id UUID NOT NULL,
    seat_id UUID NOT NULL,
    unit_price_cents INTEGER NOT NULL CHECK (unit_price_cents >= 0),
    released_at TIMESTAMPTZ,
    FOREIGN KEY (reservation_id, screening_id) REFERENCES reservation(id, screening_id),
    FOREIGN KEY (screening_id, room_id) REFERENCES screening(id, room_id),
    FOREIGN KEY (seat_id, room_id) REFERENCES seat(id, room_id),
    UNIQUE(reservation_id, seat_id)
);
-- The database arbitrates concurrent confirmation: only one active assignment per screening/seat.
CREATE UNIQUE INDEX active_screening_seat ON reservation_seat(screening_id, seat_id)
    WHERE released_at IS NULL;
CREATE INDEX reservation_customer_created ON reservation(customer_id, created_at DESC);
CREATE INDEX reservation_screening ON reservation(screening_id);
