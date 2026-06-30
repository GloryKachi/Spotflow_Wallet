package com.glory.spotflow_wallet.spotflow.dto;

/**
 * Response body shared by:
 *  - POST /transfers (TransferDetailsResponse)
 *  - GET  /transfers/reference/{reference}
 */
public record TransferDetailsResponse(
        String reference,
        String spotflowreference,
        long amount,
        String currency,
        String transferMode,
        Destination destination,
        String narrations,
        String status
) {
    public record Destination(
            String accountNumber,
            String accountName,
            String bankCode,
            String branchCode,
            String bankName
    ) {
    }
}
