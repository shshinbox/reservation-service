DROP INDEX IF EXISTS uq_reservation_seats_active_schedule_seat;

UPDATE reservation_seats
SET status = CASE status
    WHEN 'PAYMENT_PENDING' THEN 'HOLD'
    WHEN 'CONFIRMED' THEN 'RESERVED'
    WHEN 'CANCELLED' THEN 'CANCELLED'
    WHEN 'EXPIRED' THEN 'EXPIRED'
    ELSE status
END;

CREATE UNIQUE INDEX uq_reservation_seats_active_schedule_seat
ON reservation_seats (schedule_id, seat_id)
WHERE status IN ('HOLD', 'RESERVED');

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE,
    order_id VARCHAR(100) UNIQUE,
    reservation_id UUID NOT NULL,
    amount NUMERIC(19, 2),
    currency VARCHAR(3),
    status VARCHAR(30) NOT NULL,
    pg_provider VARCHAR(50),
    pg_payment_key VARCHAR(200) UNIQUE,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    requested_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payments_reservation
        FOREIGN KEY (reservation_id)
        REFERENCES reservations (reservation_id)
);

CREATE INDEX ix_payments_reservation_id
ON payments (reservation_id);

CREATE INDEX ix_payments_order_id
ON payments (order_id);

CREATE INDEX ix_payments_status
ON payments (status);
