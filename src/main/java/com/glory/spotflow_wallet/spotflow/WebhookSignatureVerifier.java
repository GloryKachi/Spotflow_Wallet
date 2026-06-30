package com.glory.spotflow_wallet.spotflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Verifies the `x-spotflow-signature` header per the Standard Webhooks spec
 * (https://standardwebhooks.com/) that Spotflow's webhook docs say they follow:
 * HMAC-SHA256 over the string "{webhook-id}.{webhook-timestamp}.{rawBody}",
 * using a webhook signing secret that's separate from the API secret key.
 *
 * NOTE: the exact secret format/prefix (e.g. a "whsec_" prefix some Standard
 * Webhooks implementations use) isn't shown in the public docs I had access to -
 * it's generated per-account when you set your webhook URL in the Spotflow
 * dashboard (Settings > API Keys). Get it from there and set it as
 * spotflow.webhook-signing-secret. If it's not configured, verification is
 * skipped with a loud warning so local/mock development isn't blocked - but
 * this MUST be set before handling real traffic, otherwise anyone who finds
 * your webhook URL can POST fake "successful" events and credit wallets for free.
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SpotflowProperties properties;

    public WebhookSignatureVerifier(SpotflowProperties properties) {
        this.properties = properties;
    }

    /**
     * @param webhookId        value of the `webhook-id` header
     * @param webhookTimestamp value of the `webhook-timestamp` header
     * @param rawBody          the exact, unmodified request body bytes as received
     * @param signatureHeader  value of the `x-spotflow-signature` header
     * @return true if verified OR if verification is intentionally skipped (no secret configured)
     */
    public boolean isValid(String webhookId, String webhookTimestamp, String rawBody, String signatureHeader) {
        String secret = properties.getWebhookSigningSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("spotflow.webhook-signing-secret is not configured - skipping signature verification. " +
                    "Set this before going anywhere near production traffic.");
            return true;
        }

        if (webhookId == null || webhookTimestamp == null || signatureHeader == null) {
            log.warn("Webhook request missing required signing headers (webhook-id / webhook-timestamp / x-spotflow-signature)");
            return false;
        }

        String signedContent = webhookId + "." + webhookTimestamp + "." + rawBody;
        String expected = computeHmac(secret, signedContent);

        // Standard Webhooks signature headers can carry multiple space-separated
        // "v1,<base64sig>" values (for secret rotation); check each candidate.
        for (String candidate : signatureHeader.split(" ")) {
            String value = candidate.contains(",") ? candidate.substring(candidate.indexOf(',') + 1) : candidate;
            if (constantTimeEquals(expected, value)) {
                return true;
            }
        }
        return false;
    }

    private String computeHmac(String secret, String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute webhook HMAC", ex);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
