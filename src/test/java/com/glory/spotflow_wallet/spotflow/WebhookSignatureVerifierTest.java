package com.glory.spotflow_wallet.spotflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookSignatureVerifierTest {

    @Mock
    SpotflowProperties properties;

    WebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WebhookSignatureVerifier(properties);
    }

    // --- Secret not configured (skip verification) ---

    @Test
    void isValid_returnsTrue_whenSecretIsNull() {
        when(properties.getWebhookSigningSecret()).thenReturn(null);
        assertThat(verifier.isValid("id", "ts", "body", "sig")).isTrue();
    }

    @Test
    void isValid_returnsTrue_whenSecretIsBlank() {
        when(properties.getWebhookSigningSecret()).thenReturn("   ");
        assertThat(verifier.isValid("id", "ts", "body", "sig")).isTrue();
    }

    // --- Missing required headers ---

    @Test
    void isValid_returnsFalse_whenWebhookIdIsNull() {
        when(properties.getWebhookSigningSecret()).thenReturn("my-secret");
        assertThat(verifier.isValid(null, "ts", "body", "v1,sig")).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenTimestampIsNull() {
        when(properties.getWebhookSigningSecret()).thenReturn("my-secret");
        assertThat(verifier.isValid("id", null, "body", "v1,sig")).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenSignatureHeaderIsNull() {
        when(properties.getWebhookSigningSecret()).thenReturn("my-secret");
        assertThat(verifier.isValid("id", "ts", "body", null)).isFalse();
    }

    // --- Correct HMAC ---

    @Test
    void isValid_returnsTrue_withCorrectHmacSignature() throws Exception {
        String secret = "test-signing-secret";
        String webhookId = "msg_001";
        String webhookTimestamp = "1700000000";
        String rawBody = "{\"event\":\"account_credit_successful\"}";
        String expectedSig = computeHmac(secret, webhookId + "." + webhookTimestamp + "." + rawBody);

        when(properties.getWebhookSigningSecret()).thenReturn(secret);
        assertThat(verifier.isValid(webhookId, webhookTimestamp, rawBody, "v1," + expectedSig)).isTrue();
    }

    @Test
    void isValid_returnsTrue_withBareBase64Signature_withoutVersionPrefix() throws Exception {
        String secret = "bare-secret";
        String webhookId = "msg_bare";
        String webhookTimestamp = "1700000001";
        String rawBody = "{}";
        String expectedSig = computeHmac(secret, webhookId + "." + webhookTimestamp + "." + rawBody);

        when(properties.getWebhookSigningSecret()).thenReturn(secret);
        // header without the "v1," prefix — the code uses the full token if no comma present
        assertThat(verifier.isValid(webhookId, webhookTimestamp, rawBody, expectedSig)).isTrue();
    }

    // --- Wrong HMAC ---

    @Test
    void isValid_returnsFalse_withIncorrectSignature() {
        when(properties.getWebhookSigningSecret()).thenReturn("my-secret");
        assertThat(verifier.isValid("id", "ts", "body", "v1,badsignature==")).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenSignedContentDiffers() throws Exception {
        String secret = "my-secret";
        // correct sig for different body
        String correctSig = computeHmac(secret, "id.ts.DIFFERENT_BODY");

        when(properties.getWebhookSigningSecret()).thenReturn(secret);
        assertThat(verifier.isValid("id", "ts", "actual-body", "v1," + correctSig)).isFalse();
    }

    // --- Multiple signatures (key rotation) ---

    @Test
    void isValid_returnsTrue_whenSecondTokenInMultiValueHeaderMatches() throws Exception {
        String secret = "rotation-secret";
        String webhookId = "msg_002";
        String webhookTimestamp = "1700000002";
        String rawBody = "{\"event\":\"test\"}";
        String validSig = computeHmac(secret, webhookId + "." + webhookTimestamp + "." + rawBody);

        String headerValue = "v1,wrongsig== v1," + validSig;
        when(properties.getWebhookSigningSecret()).thenReturn(secret);
        assertThat(verifier.isValid(webhookId, webhookTimestamp, rawBody, headerValue)).isTrue();
    }

    @Test
    void isValid_returnsFalse_whenNoTokenInMultiValueHeaderMatches() {
        when(properties.getWebhookSigningSecret()).thenReturn("my-secret");
        assertThat(verifier.isValid("id", "ts", "body", "v1,wrong1== v1,wrong2==")).isFalse();
    }

    // --- Helper ---

    private String computeHmac(String secret, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
}