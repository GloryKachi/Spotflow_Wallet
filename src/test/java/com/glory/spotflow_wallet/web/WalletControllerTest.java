package com.glory.spotflow_wallet.web;

import com.glory.spotflow_wallet.domain.transaction.Transaction;
import com.glory.spotflow_wallet.domain.transaction.TransactionType;
import com.glory.spotflow_wallet.domain.wallet.InsufficientBalanceException;
import com.glory.spotflow_wallet.domain.wallet.WalletService;
import com.glory.spotflow_wallet.spotflow.dto.CreateDynamicAccountResponse;
import com.glory.spotflow_wallet.spotflow.exception.SpotflowApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean WalletService walletService;

    // ── POST /wallet/fund ─────────────────────────────────────────────────────

    @Test
    void fund_returns200_withValidRequest() throws Exception {
        Transaction tx = Transaction.createPending(1L, TransactionType.FUNDING, "FUND-abc", BigDecimal.valueOf(500));
        CreateDynamicAccountResponse va = new CreateDynamicAccountResponse(
                "va-01", "0123456789", "JANE DOE", "Test Bank", "test", "temporary");
        when(walletService.fundWallet(anyLong(), any(BigDecimal.class)))
                .thenReturn(new WalletService.FundResult(tx, va));

        mockMvc.perform(post("/wallet/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":500.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("FUND-abc"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.virtualAccountNumber").value("0123456789"))
                .andExpect(jsonPath("$.bankName").value("Test Bank"))
                .andExpect(jsonPath("$.accountName").value("JANE DOE"));
    }

    @Test
    void fund_returns400_whenUserIdMissing() throws Exception {
        mockMvc.perform(post("/wallet/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fund_returns400_whenAmountIsZero() throws Exception {
        mockMvc.perform(post("/wallet/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fund_returns400_whenAmountIsNegative() throws Exception {
        mockMvc.perform(post("/wallet/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":-100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fund_returns400_whenAmountMissing() throws Exception {
        mockMvc.perform(post("/wallet/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fund_returns404_whenUserNotFound() throws Exception {
        when(walletService.fundWallet(anyLong(), any(BigDecimal.class)))
                .thenThrow(new NoSuchElementException("User not found: 999"));

        mockMvc.perform(post("/wallet/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"amount\":100.0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found: 999"));
    }

    @Test
    void fund_returns502_whenSpotflowFails() throws Exception {
        when(walletService.fundWallet(anyLong(), any(BigDecimal.class)))
                .thenThrow(new SpotflowApiException("Spotflow unavailable", 503));

        mockMvc.perform(post("/wallet/fund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":100.0}"))
                .andExpect(status().isBadGateway());
    }

    // ── POST /wallet/withdraw ─────────────────────────────────────────────────

    @Test
    void withdraw_returns200_withValidRequest() throws Exception {
        Transaction tx = Transaction.createPending(1L, TransactionType.WITHDRAWAL, "WDRL-xyz", BigDecimal.valueOf(200));
        when(walletService.withdraw(anyLong(), any(BigDecimal.class), anyString(), anyString(), anyString()))
                .thenReturn(new WalletService.WithdrawResult(tx, BigDecimal.valueOf(800)));

        mockMvc.perform(post("/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":200.0,\"bankAccountNumber\":\"0987654321\",\"bankCode\":\"044\",\"accountName\":\"Jane Doe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("WDRL-xyz"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.newBalance").value(800));
    }

    @Test
    void withdraw_returns400_whenBankAccountNumberIsBlank() throws Exception {
        mockMvc.perform(post("/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":200.0,\"bankAccountNumber\":\"\",\"bankCode\":\"044\",\"accountName\":\"Jane\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_returns400_whenBankCodeIsBlank() throws Exception {
        mockMvc.perform(post("/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":200.0,\"bankAccountNumber\":\"0987\",\"bankCode\":\"\",\"accountName\":\"Jane\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_returns400_whenAccountNameIsBlank() throws Exception {
        mockMvc.perform(post("/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":200.0,\"bankAccountNumber\":\"0987\",\"bankCode\":\"044\",\"accountName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_returns400_whenRequiredFieldsMissing() throws Exception {
        mockMvc.perform(post("/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":200.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_returns409_whenInsufficientBalance() throws Exception {
        when(walletService.withdraw(anyLong(), any(BigDecimal.class), anyString(), anyString(), anyString()))
                .thenThrow(new InsufficientBalanceException("Wallet 1 has insufficient balance for debit of 500"));

        mockMvc.perform(post("/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":500.0,\"bankAccountNumber\":\"0987\",\"bankCode\":\"044\",\"accountName\":\"Jane\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void withdraw_returns404_whenNoWalletExists() throws Exception {
        when(walletService.withdraw(anyLong(), any(BigDecimal.class), anyString(), anyString(), anyString()))
                .thenThrow(new NoSuchElementException("No wallet found for user: 1"));

        mockMvc.perform(post("/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":100.0,\"bankAccountNumber\":\"0987\",\"bankCode\":\"044\",\"accountName\":\"Jane\"}"))
                .andExpect(status().isNotFound());
    }
}