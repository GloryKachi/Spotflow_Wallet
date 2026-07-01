package com.glory.spotflow_wallet.webhook;

import com.glory.spotflow_wallet.domain.wallet.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
     * The gate is two-layered:
     * 1. Fast read: existsByEventId short-circuits retries without touching a write path.
     * 2. Insert + unique constraint: the DB constraint is the authoritative guard against
     *    concurrent duplicate deliveries that race past the read check simultaneously.
     *    DataIntegrityViolationException from the insert means a concurrent thread won
     *    the race, so we skip here too.
     *
     * Both paths are called directly on the repository (not via a self-call), so Spring's
     * proxy-based @Transactional applies correctly to each operation.
     */
    public void handle(SpotflowWebhookPayload payload, String idempotencyKey) {
        if (payload == null || payload.data() == null || idempotencyKey == null) {
            log.warn("Received malformed webhook payload (missing data or idempotency key), ignoring: {}", payload);
            return;
        }

        if (webhookEventRepository.existsByEventId(idempotencyKey)) {
            log.info("Duplicate webhook delivery for id {} - skipping (already processed)", idempotencyKey);
            return;
        }

        try {
            webhookEventRepository.save(new WebhookEvent(
                    idempotencyKey,
                    payload.event(),
                    payload.data().reference()
            ));
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Duplicate webhook delivery for id {} - skipping (concurrent duplicate)", idempotencyKey);
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
}
