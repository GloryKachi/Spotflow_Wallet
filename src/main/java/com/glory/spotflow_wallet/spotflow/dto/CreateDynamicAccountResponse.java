package com.glory.spotflow_wallet.spotflow.dto;

/**
 * Response body from POST /virtual-accounts/temporary (DynamicDetailsResponse schema).
 */
public record CreateDynamicAccountResponse(
        String id,
        String accountNumber,
        String accountName,
        String bankName,
        String mode,
        String lifeCycle
) {
}
