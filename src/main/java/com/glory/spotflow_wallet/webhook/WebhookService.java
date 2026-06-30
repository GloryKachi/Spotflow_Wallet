package com.glory.spotflow_wallet.webhook;

import com.glory.spotflow_wallet.domain.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookEventRepository webhookEventRepository;
    private final WalletService walletService;

    public WebhookService(WebhookEventRepository webhookEventRepository, WalletService walletService) {
        this.webhookEventRepository = webhookEventRepository;
        this.walletService = walletService;
    }

    /**
     * Idempotency gate + dispatch.
     *
     * Step 1 (the gate): try to insert a row keyed on the delivery's idempotency
     * key (Spotflow's `webhook-id` header per the Standard Webhooks spec). The
     * unique constraint on webhook_events.event_id means a duplicate delivery
     * (e.g. Spotflow retrying because our first 200 OK got lost in transit)
     * fails this insert and we bail out *before* touching the wallet - so the
     * balance is only ever credited once per real-world event, no matter how
     * many times the HTTP request is replayed.
     *
     * Step 2: only after the gate passes do we act on the event.
     */
    public void handle(SpotflowWebhookPayload payload, String idempotencyKey) {
        if (payload == null || payload.data() == null || idempotencyKey == null) {
            log.warn("Received malformed webhook payload (missing data or idempotency key), ignoring: {}", payload);
            return;
        }

        if (!tryRecordEventOnce(payload, idempotencyKey)) {
            log.info("Duplicate webhook delivery for id {} - skipping (already processed)", idempotencyKey);
            return;
        }

        if (!"account_credit_successful".equals(payload.event())) {
            log.info("Ignoring webhook event type: {}", payload.event());
            return;
        }

        SpotflowWebhookPayload.AccountEventData data = payload.data();
        if (!"successful".equalsIgnoreCase(data.status())) {
            log.info("Credit event {} not successful (status={}), not crediting wallet", idempotencyKey, data.status());
            return;
        }

        BigDecimal amount = data.amount();
        walletService.creditWalletForConfirmedPayment(data.reference(), data.spotflow_reference(), amount);
        log.info("Wallet credited for reference {} amount {}", data.reference(), amount);
    }

    @Transactional
    protected boolean tryRecordEventOnce(SpotflowWebhookPayload payload, String idempotencyKey) {
        try {
            webhookEventRepository.save(new WebhookEvent(
                    idempotencyKey,
                    payload.event(),
                    payload.data().reference()
            ));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
