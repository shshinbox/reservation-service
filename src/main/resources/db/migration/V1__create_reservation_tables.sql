CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE,
    hold_id UUID NOT NULL UNIQUE,
    schedule_id VARCHAR(100) NOT NULL,
    seat_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    hold_expires_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    expired_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL
);

CREATE UNIQUE INDEX uq_reservations_active_schedule_seat
ON reservations (schedule_id, seat_id)
WHERE status IN ('PAYMENT_PENDING', 'CONFIRMED');

CREATE INDEX ix_reservations_user_id
ON reservations (user_id);

CREATE INDEX ix_reservations_hold_expiration
ON reservations (status, hold_expires_at);

CREATE TABLE processed_kafka_events (
    event_id UUID PRIMARY KEY,
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

CREATE TABLE sold_seats (
    id BIGSERIAL PRIMARY KEY,
    reservation_id UUID NOT NULL,
    schedule_id VARCHAR(100) NOT NULL,
    seat_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    sold_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_sold_seats_reservation_id
ON sold_seats (reservation_id);

CREATE UNIQUE INDEX uq_sold_seats_active_schedule_seat
ON sold_seats (schedule_id, seat_id)
WHERE cancelled_at IS NULL;
