package com.glory.spotflow_wallet.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glory.spotflow_wallet.spotflow.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean WebhookService webhookService;
    @MockitoBean WebhookSignatureVerifier signatureVerifier;

    private static final String CREDIT_PAYLOAD =
            "{\"event\":\"account_credit_successful\",\"data\":{" +
            "\"id\":\"evt-payload-id\"," +
            "\"reference\":\"FUND-ref\"," +
            "\"spotflow_reference\":\"sf-ref\"," +
            "\"amount\":50000," +
            "\"currency\":\"NGN\"," +
            "\"type\":\"credit\"," +
            "\"status\":\"successful\"}}";

    // ── Signature verification ────────────────────────────────────────────────

    @Test
    void receive_returns401_whenSignatureIsInvalid() throws Exception {
        when(signatureVerifier.isValid(any(), any(), any(), any())).thenReturn(false);

        mockMvc.perform(post("/webhooks/spotflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "msg-bad")
                        .header("webhook-timestamp", "1700000000")
                        .header("x-spotflow-signature", "v1,badsig")
                        .content(CREDIT_PAYLOAD))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookService);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void receive_returns200_withValidSignatureAndDispatchesToService() throws Exception {
        when(signatureVerifier.isValid(any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/webhooks/spotflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "msg-001")
                        .header("webhook-timestamp", "1700000000")
                        .header("x-spotflow-signature", "v1,validhash")
                        .content(CREDIT_PAYLOAD))
                .andExpect(status().isOk());

        // idempotency key comes from the webhook-id header
        verify(webhookService).handle(any(), eq("msg-001"));
    }

    // ── Resilience: bad body ──────────────────────────────────────────────────

    @Test
    void receive_returns200_evenWhenBodyIsNotValidJson() throws Exception {
        when(signatureVerifier.isValid(any(), any(), any(), any())).thenReturn(true);

        mockMvc.perform(post("/webhooks/spotflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-id", "msg-002")
                        .header("webhook-timestamp", "1700000000")
                        .header("x-spotflow-signature", "v1,validhash")
                        .content("not-json-at-all"))
                .andExpect(status().isOk());

        // webhookService is NOT called because parsing failed before dispatch
        verifyNoInteractions(webhookService);
    }

    // ── Idempotency key fallback ──────────────────────────────────────────────

    @Test
    void receive_usesPayloadDataId_asIdempotencyKey_whenWebhookIdHeaderAbsent() throws Exception {
        when(signatureVerifier.isValid(any(), any(), any(), any())).thenReturn(true);

        // No webhook-id header — controller falls back to payload.data.id
        mockMvc.perform(post("/webhooks/spotflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-timestamp", "1700000000")
                        .header("x-spotflow-signature", "v1,validhash")
                        .content(CREDIT_PAYLOAD))
                .andExpect(status().isOk());

        verify(webhookService).handle(any(), eq("evt-payload-id"));
    }

    @Test
    void receive_passesNullIdempotencyKey_whenBothHeaderAndPayloadIdAreMissing() throws Exception {
        when(signatureVerifier.isValid(any(), any(), any(), any())).thenReturn(true);

        String payloadWithNoDataId = "{\"event\":\"account_credit_successful\",\"data\":{" +
                "\"reference\":\"ref-x\",\"spotflow_reference\":\"sf-x\"," +
                "\"amount\":1000,\"currency\":\"NGN\",\"type\":\"credit\",\"status\":\"successful\"}}";

        mockMvc.perform(post("/webhooks/spotflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("webhook-timestamp", "1700000000")
                        .header("x-spotflow-signature", "v1,validhash")
                        .content(payloadWithNoDataId))
                .andExpect(status().isOk());

        // idempotency key is null; webhookService.handle receives null and guards internally
        verify(webhookService).handle(any(), isNull());
    }
}