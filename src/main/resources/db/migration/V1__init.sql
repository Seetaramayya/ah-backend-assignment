CREATE TABLE deliveries (
    id UUID PRIMARY KEY,
    vehicle_id VARCHAR(64) NOT NULL,
    address VARCHAR(255) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_deliveries_started_at ON deliveries (started_at);

CREATE TABLE invoice_requests (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE invoice_request_items (
    id UUID PRIMARY KEY,
    invoice_request_id UUID NOT NULL REFERENCES invoice_requests (id),
    delivery_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    invoice_id UUID,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_request_items_delivery_status ON invoice_request_items (delivery_id, status);
