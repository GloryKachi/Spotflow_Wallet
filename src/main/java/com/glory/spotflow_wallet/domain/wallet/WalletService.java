package com.glory.spotflow_wallet.domain.wallet;

import com.glory.spotflow_wallet.domain.transaction.Transaction;
import com.glory.spotflow_wallet.domain.transaction.TransactionRepository;
import com.glory.spotflow_wallet.domain.transaction.TransactionType;
import com.glory.spotflow_wallet.domain.user.User;
import com.glory.spotflow_wallet.domain.user.UserRepository;
import com.glory.spotflow_wallet.spotflow.SpotflowClient;
import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountRequest;
import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountResponse;
import com.glory.spotflow_wallet.spotflow.dto.CreateTransferRequest;
import com.glory.spotflow_wallet.spotflow.dto.TransferDetailsResponse;
import com.glory.spotflow_wallet.spotflow.exception.SpotflowApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SpotflowClient spotflowClient;

    public WalletService(WalletRepository walletRepository,
                          TransactionRepository transactionRepository,
                          UserRepository userRepository,
                          SpotflowClient spotflowClient) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.spotflowClient = spotflowClient;
    }

    /**
     * Pay-In: creates a local PENDING transaction, then asks Spotflow for a
     * temporary dynamic account for the user to pay into. The wallet balance
     * is NOT touched here - it's only credited later when the webhook confirms
     * the payment actually landed.
     */
    @Transactional
    public FundResult fundWallet(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        String reference = "FUND-" + UUID.randomUUID();
        Transaction transaction = Transaction.createPending(userId, TransactionType.FUNDING, reference, amount);
        transactionRepository.save(transaction);

        // amount must be sent to Spotflow in subunits (kobo for NGN)
        long amountInSubunits = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        CreateDynamicAccountResponse account;
        try {
            account = spotflowClient.createDynamicAccount(
                    new CreateDynamicAccountRequest("NGN", user.getFullName(), amountInSubunits, null));
        } catch (SpotflowApiException ex) {
            transaction.markFailed(null);
            throw ex;
        }

        return new FundResult(transaction, account);
    }

    /**
     * Called exclusively by the webhook handler once it has confirmed (via the
     * idempotency gate) that this credit event has not already been processed.
     * Credits the wallet and marks the matching transaction SUCCESS.
     */
    @Transactional
    public void creditWalletForConfirmedPayment(String reference, String spotflowReference, BigDecimal amount) {
        Transaction transaction = transactionRepository.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new NoSuchElementException("No transaction found for reference: " + reference));

        if (transaction.getStatus() != com.glory.spotflow_wallet.domain.transaction.TransactionStatus.PENDING) {
            // Already processed (defence in depth alongside the webhook_events gate) - no-op.
            return;
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(transaction.getUserId())
                .orElseGet(() -> walletRepository.save(new Wallet(transaction.getUserId(), BigDecimal.ZERO, "NGN")));

        wallet.credit(amount);
        walletRepository.save(wallet);

        transaction.markSuccess(spotflowReference);
        transactionRepository.save(transaction);
    }

    /**
     * Payout: deducts from the local wallet balance first (so a user can never
     * withdraw more than they have, even if Spotflow's call is slow), then
     * calls Spotflow's disbursement API. If Spotflow's call fails outright,
     * the debit is rolled back since both happen in the same transaction.
     */
    @Transactional
    public WithdrawResult withdraw(Long userId, BigDecimal amount, String bankAccountNumber, String bankCode, String accountName) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new NoSuchElementException("No wallet found for user: " + userId));

        wallet.debit(amount);
        walletRepository.save(wallet);

        String reference = "WDRL-" + UUID.randomUUID();
        Transaction transaction = Transaction.createPending(userId, TransactionType.WITHDRAWAL, reference, amount);
        transactionRepository.save(transaction);

        long amountInSubunits = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

        TransferDetailsResponse transfer;
        try {
            transfer = spotflowClient.createTransfer(new CreateTransferRequest(
                    reference,
                    amountInSubunits,
                    "NGN",
                    "bank_transfer",
                    new CreateTransferRequest.Destination(bankAccountNumber, accountName, bankCode, null),
                    "Wallet withdrawal for user " + userId
            ));
        } catch (SpotflowApiException ex) {
            // Spotflow rejected the disbursement outright - refund the wallet and mark failed.
            wallet.credit(amount);
            walletRepository.save(wallet);
            transaction.markFailed(null);
            transactionRepository.save(transaction);
            throw ex;
        }

        if ("successful".equalsIgnoreCase(transfer.status())) {
            transaction.markSuccess(transfer.spotflowreference());
        }
        // Otherwise leave as PENDING - the reconciliation worker and/or the
        // account_debit_successful webhook will resolve it.
        transactionRepository.save(transaction);

        return new WithdrawResult(transaction, wallet.getBalance());
    }

    /**
     * Used by the reconciliation worker when Spotflow confirms a withdrawal
     * actually failed - the up-front debit made at request time needs reversing.
     */
    @Transactional
    public void refundFailedWithdrawal(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new NoSuchElementException("No wallet found for user: " + userId));
        wallet.credit(amount);
        walletRepository.save(wallet);
    }

    public record FundResult(Transaction transaction, CreateDynamicAccountResponse virtualAccount) {
    }

    public record WithdrawResult(Transaction transaction, BigDecimal newBalance) {
    }
}
