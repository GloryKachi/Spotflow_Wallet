package com.glory.spotflow_wallet.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glory.spotflow_wallet.spotflow.WebhookSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;
    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookService webhookService,
                              WebhookSignatureVerifier signatureVerifier,
                              ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

    /**
     * Takes the raw body as a String (rather than binding straight to a DTO) because
     * HMAC verification must run over the exact bytes Spotflow signed - re-serializing
     * a deserialized object would not reliably reproduce the original byte-for-byte body.
     *
     * Header names follow the Standard Webhooks spec Spotflow says it implements:
     * webhook-id, webhook-timestamp, and Spotflow's own x-spotflow-signature.
     *
     * Always returns 200 once the request is durably handled (or safely ignored),
     * so Spotflow doesn't retry forever - the one exception is a failed signature
     * check, which returns 401 so a forged request is rejected outright.
     */
    @PostMapping("/spotflow")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp,
            @RequestHeader(value = "x-spotflow-signature", required = false) String signature,
            @RequestBody String rawBody) {

        if (!signatureVerifier.isValid(webhookId, webhookTimestamp, rawBody, signature)) {
            log.warn("Rejected webhook delivery with invalid signature (webhook-id={})", webhookId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SpotflowWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, SpotflowWebhookPayload.class);
        } catch (Exception ex) {
            log.warn("Could not parse webhook payload, acknowledging anyway to stop retries: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }

        // Prefer the Standard Webhooks `webhook-id` header as the idempotency key
        // (the documented mechanism); fall back to the payload's own data.id if
        // the header is missing for any reason, so the gate still holds.
        String idempotencyKey = (webhookId != null) ? webhookId
                : (payload.data() != null ? payload.data().id() : null);

        webhookService.handle(payload, idempotencyKey);
        return ResponseEntity.ok().build();
    }
}
