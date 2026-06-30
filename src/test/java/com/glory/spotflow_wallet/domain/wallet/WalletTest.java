package com.glory.spotflow_wallet.domain.wallet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    @Test
    void credit_addsAmountToBalance() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(100), "NGN");
        wallet.credit(BigDecimal.valueOf(50));
        assertThat(wallet.getBalance()).isEqualByComparingTo("150");
    }

    @Test
    void credit_updatesUpdatedAt() {
        Wallet wallet = new Wallet(1L, BigDecimal.ZERO, "NGN");
        wallet.credit(BigDecimal.ONE);
        assertThat(wallet.getUpdatedAt()).isNotNull();
    }

    @Test
    void debit_subtractsAmountFromBalance() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(200), "NGN");
        wallet.debit(BigDecimal.valueOf(80));
        assertThat(wallet.getBalance()).isEqualByComparingTo("120");
    }

    @Test
    void debit_exactBalance_leavesZero() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(100), "NGN");
        wallet.debit(BigDecimal.valueOf(100));
        assertThat(wallet.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void debit_updatesUpdatedAt() {
        Wallet wallet = new Wallet(1L, BigDecimal.TEN, "NGN");
        wallet.debit(BigDecimal.ONE);
        assertThat(wallet.getUpdatedAt()).isNotNull();
    }

    @Test
    void debit_throwsInsufficientBalanceException_whenAmountExceedsBalance() {
        Wallet wallet = new Wallet(1L, BigDecimal.valueOf(50), "NGN");
        assertThatThrownBy(() -> wallet.debit(BigDecimal.valueOf(51)))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void debit_throwsInsufficientBalanceException_whenBalanceIsZero() {
        Wallet wallet = new Wallet(1L, BigDecimal.ZERO, "NGN");
        assertThatThrownBy(() -> wallet.debit(BigDecimal.ONE))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void constructor_setsAllFields() {
        Wallet wallet = new Wallet(7L, BigDecimal.valueOf(999), "NGN");
        assertThat(wallet.getUserId()).isEqualTo(7L);
        assertThat(wallet.getBalance()).isEqualByComparingTo("999");
        assertThat(wallet.getCurrency()).isEqualTo("NGN");
    }
}
