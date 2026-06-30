package com.glory.spotflow_wallet.domain.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    @Test
    void createPending_setsCorrectInitialState() {
        Transaction tx = Transaction.createPending(1L, TransactionType.FUNDING, "FUND-ref-001", BigDecimal.valueOf(500));

        assertThat(tx.getUserId()).isEqualTo(1L);
        assertThat(tx.getType()).isEqualTo(TransactionType.FUNDING);
        assertThat(tx.getReference()).isEqualTo("FUND-ref-001");
        assertThat(tx.getAmount()).isEqualByComparingTo("500");
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(tx.getCreatedAt()).isNotNull();
        assertThat(tx.getUpdatedAt()).isNotNull();
        assertThat(tx.getSpotflowReference()).isNull();
    }

    @Test
    void markSuccess_setsStatusAndSpotflowReference() {
        Transaction tx = Transaction.createPending(1L, TransactionType.FUNDING, "ref-002", BigDecimal.TEN);

        tx.markSuccess("sf-ref-abc");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(tx.getSpotflowReference()).isEqualTo("sf-ref-abc");
        assertThat(tx.getUpdatedAt()).isNotNull();
    }

    @Test
    void markSuccess_withNullSpotflowReference_setsStatusOnly() {
        Transaction tx = Transaction.createPending(1L, TransactionType.FUNDING, "ref-003", BigDecimal.TEN);

        tx.markSuccess(null);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(tx.getSpotflowReference()).isNull();
    }

    @Test
    void markFailed_setsStatusAndSpotflowReference() {
        Transaction tx = Transaction.createPending(2L, TransactionType.WITHDRAWAL, "ref-004", BigDecimal.valueOf(200));

        tx.markFailed("sf-ref-err");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getSpotflowReference()).isEqualTo("sf-ref-err");
        assertThat(tx.getUpdatedAt()).isNotNull();
    }

    @Test
    void markFailed_withNullSpotflowReference_setsStatusOnly() {
        Transaction tx = Transaction.createPending(2L, TransactionType.WITHDRAWAL, "ref-005", BigDecimal.TEN);

        tx.markFailed(null);

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getSpotflowReference()).isNull();
    }

    @Test
    void markAbandoned_setsStatusToAbandoned() {
        Transaction tx = Transaction.createPending(3L, TransactionType.FUNDING, "ref-006", BigDecimal.valueOf(100));

        tx.markAbandoned();

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.ABANDONED);
        assertThat(tx.getUpdatedAt()).isNotNull();
    }

    @Test
    void createPending_forWithdrawalType_setsTypeCorrectly() {
        Transaction tx = Transaction.createPending(5L, TransactionType.WITHDRAWAL, "WDRL-ref-007", BigDecimal.valueOf(750));

        assertThat(tx.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }
}
