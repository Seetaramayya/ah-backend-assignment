-- invoice_requests was only ever a "submitted together" grouping row that nothing read back.
-- Invoice items are tracked and polled per delivery, so the parent table (and its FK) are dropped.
ALTER TABLE invoice_request_items
    DROP COLUMN invoice_request_id;

DROP TABLE invoice_requests;
