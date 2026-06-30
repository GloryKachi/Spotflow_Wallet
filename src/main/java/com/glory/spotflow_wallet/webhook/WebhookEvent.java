package com.glory.spotflow_wallet.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One row per webhook delivery we've successfully processed, keyed by Spotflow's
 * own event/payload id. This is the idempotency gate: we try to INSERT first
 * (unique constraint on event_id); if that fails because the row already exists,
 * we know this is a duplicate delivery (e.g. a network retry) and skip processing,
 * so the wallet is never credited twice for the same event.
 */
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "reference")
    private String reference;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected WebhookEvent() {
        // for JPA
    }

    public WebhookEvent(String eventId, String eventType, String reference) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.reference = reference;
        this.receivedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }
}
