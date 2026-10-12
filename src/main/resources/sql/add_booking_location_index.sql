-- Hibernate (ddl-auto: update) will create this index automatically on next
-- boot because it is declared on the Booking entity. This script is kept as
-- a manual-apply reference for environments where ddl-auto is ever disabled,
-- matching the convention used by add_inbound_event_identity.sql.

CREATE INDEX IF NOT EXISTS idx_booking_location
    ON bookings (location_id);
