package com.glory.spotflow_wallet.spotflow.dto;

/**
 * Request body for POST /transfers (Create Single Bank Disbursement).
 */
public record CreateTransferRequest(
        String reference,
        long amount,
        String currency,
        String type,
        Destination destination,
        String narrations
) {
    public record Destination(
            String accountNumber,
            String accountName,
            String bankCode,
            String branchCode
    ) {
    }
}
