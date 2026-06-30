package com.glory.spotflow_wallet.worker;

import com.glory.spotflow_wallet.domain.transaction.Transaction;
import com.glory.spotflow_wallet.domain.transaction.TransactionRepository;
import com.glory.spotflow_wallet.domain.transaction.TransactionStatus;
import com.glory.spotflow_wallet.domain.transaction.TransactionType;
import com.glory.spotflow_wallet.domain.wallet.WalletService;
import com.glory.spotflow_wallet.spotflow.SpotflowClient;
import com.glory.spotflow_wallet.spotflow.dto.TransferDetailsResponse;
import com.glory.spotflow_wallet.spotflow.exception.SpotflowApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook fallback loop.
 *
 * Webhooks can fail or be delayed indefinitely, so this worker periodically
 * sweeps for transactions stuck PENDING for over an hour, asks Spotflow
 * directly what really happened, and reconciles the local record:
 *  - If Spotflow confirms success -> apply it (mark success for withdrawals,
 *    which were already debited up-front when the request was made).
 *  - If Spotflow has no record / it's still not successful -> mark ABANDONED.
 *
 * Runs every 10 minutes; the 1-hour staleness window is what the task asks for.
 * In a real production system this would be backed by a distributed lock so
 * only one instance runs it, but that's out of scope for this demo.
 */
@Component
public class PendingTransactionReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionReconciliationWorker.class);
    private static final long STALE_AFTER_MINUTES = 60;

    private final TransactionRepository transactionRepository;
    private final SpotflowClient spotflowClient;
    private final WalletService walletService;

    public PendingTransactionReconciliationWorker(TransactionRepository transactionRepository,
                                                    SpotflowClient spotflowClient,
                                                    WalletService walletService) {
        this.transactionRepository = transactionRepository;
        this.spotflowClient = spotflowClient;
        this.walletService = walletService;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void reconcileStuckTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_AFTER_MINUTES);
        List<Transaction> stuck = transactionRepository.findByStatusAndCreatedAtBefore(TransactionStatus.PENDING, cutoff);

        if (stuck.isEmpty()) {
            return;
        }
        log.info("Reconciliation worker found {} stuck PENDING transaction(s)", stuck.size());

        for (Transaction transaction : stuck) {
            try {
                reconcileOne(transaction);
            } catch (Exception ex) {
                // Never let one bad transaction stop the sweep of the rest.
                log.error("Failed to reconcile transaction {}: {}", transaction.getReference(), ex.getMessage(), ex);
            }
        }
    }

    private void reconcileOne(Transaction transaction) {
        // Withdrawals map directly to Spotflow's /transfers/reference/{reference} lookup.
        // Fundings (dynamic-account pay-ins) don't have an equivalent "status by our
        // reference" endpoint in Spotflow's transfer API - in practice they're confirmed
        // exclusively via the account_credit_successful webhook, so for fundings we can
        // only apply the timeout-based ABANDONED rule below.
        if (transaction.getType() == TransactionType.WITHDRAWAL) {
            reconcileWithdrawal(transaction);
        } else {
            abandonIfStillPending(transaction);
        }
    }

    @Transactional
    public void reconcileWithdrawal(Transaction transaction) {
        TransferDetailsResponse status;
        try {
            status = spotflowClient.getTransferByReference(transaction.getReference());
        } catch (SpotflowApiException ex) {
            log.warn("Spotflow lookup failed for {}: {}", transaction.getReference(), ex.getMessage());
            abandonIfStillPending(transaction);
            return;
        }

        if (status == null || status.status() == null || "not_found".equalsIgnoreCase(status.status())) {
            abandonIfStillPending(transaction);
            return;
        }

        if ("successful".equalsIgnoreCase(status.status())) {
            transaction.markSuccess(status.spotflowreference());
            transactionRepository.save(transaction);
            log.info("Reconciled withdrawal {} as SUCCESS via Spotflow lookup", transaction.getReference());
        } else if ("failed".equalsIgnoreCase(status.status())) {
            // Refund the wallet since the up-front debit never actually left the platform.
            walletService.refundFailedWithdrawal(transaction.getUserId(), transaction.getAmount());
            transaction.markFailed(status.spotflowreference());
            transactionRepository.save(transaction);
            log.info("Reconciled withdrawal {} as FAILED, refunded wallet", transaction.getReference());
        } else {
            // Still genuinely pending on Spotflow's side - leave it for the next sweep
            // unless it has crossed the abandon window, handled below.
            abandonIfStillPending(transaction);
        }
    }

    private void abandonIfStillPending(Transaction transaction) {
        transaction.markAbandoned();
        transactionRepository.save(transaction);
        log.info("Marked transaction {} ABANDONED after exceeding {} minute staleness window",
                transaction.getReference(), STALE_AFTER_MINUTES);
    }
}
