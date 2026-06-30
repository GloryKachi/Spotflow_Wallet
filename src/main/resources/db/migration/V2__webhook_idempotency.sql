CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(150) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    reference VARCHAR(100),
    received_at TIMESTAMP NOT NULL DEFAULT now()
);
