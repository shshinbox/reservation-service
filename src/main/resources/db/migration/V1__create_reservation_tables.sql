CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE,
    confirmation_id VARCHAR(100) NOT NULL UNIQUE,
    schedule_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    expired_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reservation_seats (
    id BIGSERIAL PRIMARY KEY,
    reservation_id UUID NOT NULL,
    schedule_id VARCHAR(100) NOT NULL,
    seat_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_reservation_seats_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES reservations (reservation_id)
);

CREATE UNIQUE INDEX uq_reservation_seats_reservation_seat
ON reservation_seats (reservation_id, seat_id);

CREATE UNIQUE INDEX uq_reservation_seats_active_schedule_seat
ON reservation_seats (schedule_id, seat_id)
WHERE status IN ('PAYMENT_PENDING', 'CONFIRMED');

CREATE INDEX ix_reservation_seats_reservation_id
ON reservation_seats (reservation_id);

CREATE INDEX ix_reservations_user_id
ON reservations (user_id);

CREATE INDEX ix_reservations_payment_expiration
ON reservations (status, payment_expires_at);

CREATE TABLE processed_kafka_events (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_no INTEGER NOT NULL,
    offset_no BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reservation_status_histories (
    id BIGSERIAL PRIMARY KEY,
    reservation_id UUID NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_reservation_status_histories_reservation_id
ON reservation_status_histories (reservation_id);
