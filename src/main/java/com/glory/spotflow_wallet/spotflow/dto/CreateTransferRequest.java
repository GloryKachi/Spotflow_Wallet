package com.glory.spotflow_wallet.spotflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Destination(
            String accountNumber,
            String accountName,
            String bankCode,
            String branchCode
    ) {
    }
}
