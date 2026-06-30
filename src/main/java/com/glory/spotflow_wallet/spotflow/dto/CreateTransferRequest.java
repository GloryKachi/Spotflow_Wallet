package com.glory.spotflow_wallet.spotflow.dto;

/**
 * Request body for POST /transfers (Create Single Bank Disbursement).
 */
public record CreateTransferRequest(
        String reference,
        long amount,
        String currency,
        String type,
        Source source,
        Destination destination,
        String narrations
) {
    public record Source(String accountNumber) {
    }

    public record Destination(
            String accountNumber,
            String accountName,
            String bankCode,
            String branchCode
    ) {
    }
}
