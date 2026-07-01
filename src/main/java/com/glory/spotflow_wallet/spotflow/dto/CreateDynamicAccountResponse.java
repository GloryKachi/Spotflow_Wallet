package com.glory.spotflow_wallet.spotflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from POST /virtual-accounts/temporary (DynamicDetailsResponse schema).
 */
public record CreateDynamicAccountResponse(
        @JsonProperty("id") String id,
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("accountName") String accountName,
        @JsonProperty("bankName") String bankName,
        @JsonProperty("mode") String mode,
        @JsonProperty("lifecycle") String lifeCycle
) {
}