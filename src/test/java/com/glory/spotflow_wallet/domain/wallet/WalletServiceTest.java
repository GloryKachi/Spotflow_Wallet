package com.glory.spotflow_wallet.domain.wallet;

import com.glory.spotflow_wallet.domain.transaction.Transaction;
import com.glory.spotflow_wallet.domain.transaction.TransactionRepository;
import com.glory.spotflow_wallet.domain.transaction.TransactionStatus;
import com.glory.spotflow_wallet.domain.transaction.TransactionType;
import com.glory.spotflow_wallet.domain.user.User;
import com.glory.spotflow_wallet.domain.user.UserRepository;
import com.glory.spotflow_wallet.spotflow.SpotflowClient;
import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountResponse;
import com.glory.spotflow_wallet.spotflow.dto.TransferDetailsResponse;
import com.glory.spotflow_wallet.spotflow.exception.SpotflowApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock UserRepository userRepository;
    @Mock SpotflowClient spotflowClient;

    WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(
                walletRepository, transactionRepository, userRepository, spotflowClient);
    }

    // ── fundWallet ────────────────────────────────────────────────────────────

    @Test
    void fundWallet_createsPendingTransactionAndReturnsVirtualAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser()));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateDynamicAccountResponse virtualAccount = fakeVirtualAccount();
        when(spotflowClient.createDynamicAccount(any())).thenReturn(virtualAccount);

        WalletService.FundResult result = walletService.fundWallet(1L, BigDecimal.valueOf(500));

        assertThat(result.transaction().getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.transaction().getType()).isEqualTo(TransactionType.FUNDING);
        assertThat(result.transaction().getAmount()).isEqualByComparingTo("500");
        assertThat(result.transaction().getReference()).startsWith("FUND-");
        assertThat(result.virtualAccount()).isSameAs(virtualAccount);
    }

    @Test
    void fundWallet_throwsNoSuchElementException_whenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.fundWallet(99L, BigDecimal.TEN))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void fundWallet_rethrowsSpotflowException_whenCreateDynamicAccountFails() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser()));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(spotflowClient.createDynamicAccount(any()))
                .thenThrow(new SpotflowApiException("gateway error", 503));

        assertThatThrownBy(() -> walletService.fundWallet(1L, BigDecimal.valueOf(200)))
                .isInstanceOf(SpotflowApiException.class);
    }

    // ── creditWalletForConfirmedPayment ───────────────────────────────────────

    @Test
    void creditWalletForConfirmedPayment_creditsExistingWalletAndMarksSuccess() {
        Transaction pendingTx = Transaction.createPending(1L, TransactionType.FUNDING, "FUND-ref", BigDecimal.valueOf(300));
        when(transactionRepository.findByReferenceForUpdate("FUND-ref")).thenReturn(Optional.of(pendingTx));
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(100), "NGN");
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.creditWalletForConfirmedPayment("FUND-ref", "sf-ref-x", BigDecimal.valueOf(300));

        assertThat(wallet.getBalance()).isEqualByComparingTo("400");
        assertThat(pendingTx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(pendingTx.getSpotflowReference()).isEqualTo("sf-ref-x");
    }

    @Test
    void creditWalletForConfirmedPayment_createsNewWallet_whenNoneExists() {
        Transaction pendingTx = Transaction.createPending(2L, TransactionType.FUNDING, "FUND-new", BigDecimal.valueOf(100));
        when(transactionRepository.findByReferenceForUpdate("FUND-new")).thenReturn(Optional.of(pendingTx));
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.creditWalletForConfirmedPayment("FUND-new", "sf-ref-y", BigDecimal.valueOf(100));

        // first save creates the wallet, second save persists the credit
        verify(walletRepository, times(2)).save(any(Wallet.class));
    }

    @Test
    void creditWalletForConfirmedPayment_isNoOp_whenTransactionAlreadySucceeded() {
        Transaction already = Transaction.createPending(1L, TransactionType.FUNDING, "FUND-done", BigDecimal.TEN);
        already.markSuccess("sf-ref-already");
        when(transactionRepository.findByReferenceForUpdate("FUND-done")).thenReturn(Optional.of(already));

        walletService.creditWalletForConfirmedPayment("FUND-done", "sf-ref-dup", BigDecimal.TEN);

        verify(walletRepository, never()).findByUserIdForUpdate(anyLong());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void creditWalletForConfirmedPayment_throwsNoSuchElementException_whenTransactionNotFound() {
        when(transactionRepository.findByReferenceForUpdate("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                walletService.creditWalletForConfirmedPayment("MISSING", "sf", BigDecimal.TEN))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── withdraw ──────────────────────────────────────────────────────────────

    @Test
    void withdraw_debitsWalletAndReturnsPendingTransaction() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(1000), "NGN");
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(spotflowClient.createTransfer(any())).thenReturn(pendingTransfer());

        WalletService.WithdrawResult result = walletService.withdraw(
                1L, BigDecimal.valueOf(200), "0987654321", "044", "Jane Doe");

        assertThat(result.newBalance()).isEqualByComparingTo("800");
        assertThat(result.transaction().getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(result.transaction().getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.transaction().getReference()).startsWith("WDRL-");
    }

    @Test
    void withdraw_marksTransactionSuccess_whenSpotflowReturnsSuccessful() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(500), "NGN");
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(spotflowClient.createTransfer(any())).thenReturn(successfulTransfer("sf-ref-w"));

        WalletService.WithdrawResult result = walletService.withdraw(
                1L, BigDecimal.valueOf(100), "0987654321", "044", "Jane Doe");

        assertThat(result.transaction().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(result.transaction().getSpotflowReference()).isEqualTo("sf-ref-w");
    }

    @Test
    void withdraw_throwsInsufficientBalanceException_whenBalanceTooLow() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(50), "NGN");
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() ->
                walletService.withdraw(1L, BigDecimal.valueOf(100), "0987", "044", "Jane"))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(spotflowClient, never()).createTransfer(any());
    }

    @Test
    void withdraw_refundsWalletAndRethrows_whenSpotflowFails() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(300), "NGN");
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(spotflowClient.createTransfer(any()))
                .thenThrow(new SpotflowApiException("bank error", 502));

        assertThatThrownBy(() ->
                walletService.withdraw(1L, BigDecimal.valueOf(200), "0987", "044", "Jane"))
                .isInstanceOf(SpotflowApiException.class);

        // debit of 200 was reversed by the credit-back inside the catch block
        assertThat(wallet.getBalance()).isEqualByComparingTo("300");
    }

    @Test
    void withdraw_throwsNoSuchElementException_whenNoWalletExists() {
        when(walletRepository.findByUserIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                walletService.withdraw(99L, BigDecimal.TEN, "0987", "044", "Jane"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── refundFailedWithdrawal ────────────────────────────────────────────────

    @Test
    void refundFailedWithdrawal_creditsWallet() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(50), "NGN");
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.refundFailedWithdrawal(1L, BigDecimal.valueOf(150));

        assertThat(wallet.getBalance()).isEqualByComparingTo("200");
        verify(walletRepository).save(wallet);
    }

    @Test
    void refundFailedWithdrawal_throwsNoSuchElementException_whenNoWalletExists() {
        when(walletRepository.findByUserIdForUpdate(88L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.refundFailedWithdrawal(88L, BigDecimal.TEN))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User testUser() {
        return new User("Jane Doe", "0123456789", "058");
    }

    private CreateDynamicAccountResponse fakeVirtualAccount() {
        return new CreateDynamicAccountResponse(
                "va-01", "0000000001", "JANE DOE", "Test Bank", "test", "temporary");
    }

    private TransferDetailsResponse pendingTransfer() {
        return new TransferDetailsResponse(
                "WDRL-ref", "sf-ref-pending", 20000L, "NGN", "bank_transfer",
                new TransferDetailsResponse.Destination("0987654321", "Jane Doe", "044", null, "GTBank"),
                "Wallet withdrawal", "pending");
    }

    private TransferDetailsResponse successfulTransfer(String sfRef) {
        return new TransferDetailsResponse(
                "WDRL-ref", sfRef, 10000L, "NGN", "bank_transfer",
                new TransferDetailsResponse.Destination("0987654321", "Jane Doe", "044", null, "GTBank"),
                "Wallet withdrawal", "successful");
    }
}