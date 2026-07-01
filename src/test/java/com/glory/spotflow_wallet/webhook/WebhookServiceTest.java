package com.glory.spotflow_wallet.webhook;

import com.glory.spotflow_wallet.domain.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock WebhookEventRepository webhookEventRepository;
    @Mock WalletService walletService;
    @InjectMocks WebhookService webhookService;

    // ── Guard: malformed input ────────────────────────────────────────────────

    @Test
    void handle_ignoresNullPayload() {
        webhookService.handle(null, "id-001");

        verifyNoInteractions(walletService, webhookEventRepository);
    }

    @Test
    void handle_ignoresPayloadWithNullData() {
        SpotflowWebhookPayload payload = new SpotflowWebhookPayload("account_credit_successful", null);

        webhookService.handle(payload, "id-002");

        verifyNoInteractions(walletService, webhookEventRepository);
    }

    @Test
    void handle_ignoresNullIdempotencyKey() {
        SpotflowWebhookPayload payload = creditPayload("ref-x", "successful");

        webhookService.handle(payload, null);

        verifyNoInteractions(walletService, webhookEventRepository);
    }

    // ── Idempotency gate ──────────────────────────────────────────────────────

    @Test
    void handle_skipsDuplicateDelivery_andDoesNotCreditWallet() {
        SpotflowWebhookPayload payload = creditPayload("ref-dup", "successful");
        when(webhookEventRepository.existsByEventId("id-003")).thenReturn(true);

        webhookService.handle(payload, "id-003");

        verify(webhookEventRepository, never()).save(any());
        verifyNoInteractions(walletService);
    }

    // ── Event type filtering ──────────────────────────────────────────────────

    @Test
    void handle_ignoresNonCreditEventType() {
        SpotflowWebhookPayload payload = new SpotflowWebhookPayload("account_debit_successful",
                new SpotflowWebhookPayload.AccountEventData(
                        "id-x", "ref-x", "sf-x", BigDecimal.valueOf(5000), "NGN", "debit", "successful", null));
        when(webhookEventRepository.save(any())).thenReturn(null);

        webhookService.handle(payload, "id-004");

        verifyNoInteractions(walletService);
    }

    @Test
    void handle_ignoresUnknownEventType() {
        SpotflowWebhookPayload payload = new SpotflowWebhookPayload("some_other_event",
                new SpotflowWebhookPayload.AccountEventData(
                        "id-z", "ref-z", "sf-z", BigDecimal.valueOf(1000), "NGN", "other", "successful", null));
        when(webhookEventRepository.save(any())).thenReturn(null);

        webhookService.handle(payload, "id-005");

        verifyNoInteractions(walletService);
    }

    // ── Status filtering ──────────────────────────────────────────────────────

    @Test
    void handle_doesNotCreditWallet_whenCreditStatusIsNotSuccessful() {
        SpotflowWebhookPayload payload = creditPayload("ref-fail", "failed");
        when(webhookEventRepository.save(any())).thenReturn(null);

        webhookService.handle(payload, "id-006");

        verifyNoInteractions(walletService);
    }

    @Test
    void handle_doesNotCreditWallet_whenCreditStatusIsPending() {
        SpotflowWebhookPayload payload = creditPayload("ref-pend", "pending");
        when(webhookEventRepository.save(any())).thenReturn(null);

        webhookService.handle(payload, "id-007");

        verifyNoInteractions(walletService);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void handle_creditsWallet_forSuccessfulCreditEvent() {
        SpotflowWebhookPayload payload = new SpotflowWebhookPayload("account_credit_successful",
                new SpotflowWebhookPayload.AccountEventData(
                        "id-y", "FUND-ref-007", "sf-ref-007", BigDecimal.valueOf(50000), "NGN", "credit", "successful", null));
        when(webhookEventRepository.save(any())).thenReturn(null);

        webhookService.handle(payload, "id-008");

        verify(walletService).creditWalletForConfirmedPayment(
                "FUND-ref-007", "sf-ref-007", BigDecimal.valueOf(50000));
    }

    @Test
    void handle_creditsWallet_withStatusCaseInsensitive() {
        SpotflowWebhookPayload payload = new SpotflowWebhookPayload("account_credit_successful",
                new SpotflowWebhookPayload.AccountEventData(
                        "id-c", "FUND-ref-008", "sf-ref-008", BigDecimal.valueOf(10000), "NGN", "credit", "SUCCESSFUL", null));
        when(webhookEventRepository.save(any())).thenReturn(null);

        webhookService.handle(payload, "id-009");

        verify(walletService).creditWalletForConfirmedPayment(
                eq("FUND-ref-008"), eq("sf-ref-008"), any(BigDecimal.class));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private SpotflowWebhookPayload creditPayload(String reference, String status) {
        return new SpotflowWebhookPayload("account_credit_successful",
                new SpotflowWebhookPayload.AccountEventData(
                        "id-" + reference, reference, "sf-" + reference,
                        BigDecimal.valueOf(10000), "NGN", "credit", status, null));
    }
}