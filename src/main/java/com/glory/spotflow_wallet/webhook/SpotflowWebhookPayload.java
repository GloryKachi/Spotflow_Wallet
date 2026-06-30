package com.glory.spotflow_wallet.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Generic envelope for any Spotflow webhook: {"event": "...", "data": {...}}.
 * We only deeply model the "account_credit_successful" payload (pay-in completion)
 * since that's the one this task requires us to act on; other event types are
 * acknowledged (200 OK) but ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotflowWebhookPayload(
        String event,
        AccountEventData data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountEventData(
            String id,
            String reference,
            String spotflow_reference,
            BigDecimal amount,
            String currency,
            String type,
            String status,
            String source
    ) {
    }
}
