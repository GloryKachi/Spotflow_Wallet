package com.glory.spotflow_wallet.spotflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All Spotflow-related configuration lives here, isolated from core business config.
 * Bind from application.properties keys prefixed with "spotflow.".
 */
@ConfigurationProperties(prefix = "spotflow")
public class SpotflowProperties {

    /**
     * Secret API key (sk_test_... or sk_live_...). Never log this value.
     * Set via env var SPOTFLOW_SECRET_KEY - see application.properties.
     */
    private String secretKey;

    /**
     * Base URL for the Spotflow API, e.g. https://api.spotflow.co/api/v1
     */
    private String baseUrl;

    /**
     * When true, the MockSpotflowClient is used instead of real HTTP calls.
     * Lets the whole app be demoed/tested before sandbox keys are available.
     */
    private boolean mock;

    /**
     * The merchant's main Spotflow account number, used as the `source.accountNumber`
     * for disbursements (payouts). Required once mock=false.
     */
    private String payoutSourceAccountNumber;

    /**
     * Shared secret used to verify the x-spotflow-signature header on incoming
     * webhooks (Standard Webhooks spec: HMAC-SHA256 over "{id}.{timestamp}.{body}").
     * This is a DIFFERENT value from the API secret key - it's generated when you
     * configure your webhook URL in Settings > API Keys on the Spotflow dashboard.
     * If left blank, signature verification is skipped (with a loud warning) so
     * local/mock development still works without it.
     */
    private String webhookSigningSecret;

    public String getWebhookSigningSecret() {
        return webhookSigningSecret;
    }

    public void setWebhookSigningSecret(String webhookSigningSecret) {
        this.webhookSigningSecret = webhookSigningSecret;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getPayoutSourceAccountNumber() {
        return payoutSourceAccountNumber;
    }

    public void setPayoutSourceAccountNumber(String payoutSourceAccountNumber) {
        this.payoutSourceAccountNumber = payoutSourceAccountNumber;
    }
}
