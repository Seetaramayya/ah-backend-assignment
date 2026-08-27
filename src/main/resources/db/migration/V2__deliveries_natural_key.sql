-- A delivery is uniquely identified by (vehicle_id, started_at): a vehicle can only start one
-- delivery at a given instant. This makes POST /deliveries and POST /deliveries/v2 safe to retry:
-- a repeated creation with the same payload hits this constraint instead of inserting a duplicate.
ALTER TABLE deliveries
    ADD CONSTRAINT uq_deliveries_vehicle_started_at UNIQUE (vehicle_id, started_at);
