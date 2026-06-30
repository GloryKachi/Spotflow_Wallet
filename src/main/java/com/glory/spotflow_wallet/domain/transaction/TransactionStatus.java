package com.glory.spotflow_wallet.domain.transaction;

public enum TransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
    /**
     * Set by the reconciliation worker when a transaction has been PENDING for
     * over an hour and Spotflow has no record of it ever completing.
     */
    ABANDONED
}
