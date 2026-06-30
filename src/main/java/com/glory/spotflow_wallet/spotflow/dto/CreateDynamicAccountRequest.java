package com.glory.spotflow_wallet.spotflow.dto;

/**
 * Request body for POST /virtual-accounts/temporary
 * Amount must be in the subunit of the currency (e.g. kobo for NGN).
 */
public record CreateDynamicAccountRequest(
        String currency,
        String accountName,
        long amount,
        Integer expiresIn
) {
}
