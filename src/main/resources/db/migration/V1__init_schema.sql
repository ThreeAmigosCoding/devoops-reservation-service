-- PostgreSQL named enum for reservation status
CREATE TYPE reservation_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED');

-- Reservations table
CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_id UUID NOT NULL,
    guest_id UUID NOT NULL,
    host_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    guest_count INTEGER NOT NULL,
    total_price NUMERIC(12, 2) NOT NULL,
    status reservation_status NOT NULL DEFAULT 'PENDING',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Indexes for common queries
CREATE INDEX idx_reservations_accommodation_id ON reservations(accommodation_id);
CREATE INDEX idx_reservations_guest_id ON reservations(guest_id);
CREATE INDEX idx_reservations_host_id ON reservations(host_id);
CREATE INDEX idx_reservations_status ON reservations(status);
CREATE INDEX idx_reservations_dates ON reservations(start_date, end_date);

-- Composite index for overlap queries (only non-deleted reservations)
CREATE INDEX idx_reservations_accommodation_dates
    ON reservations(accommodation_id, start_date, end_date)
    WHERE is_deleted = false;