package com.glory.spotflow_wallet.spotflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for POST /virtual-accounts/temporary
 * Amount must be in the subunit of the currency (e.g. kobo for NGN).
 * expiresIn is omitted from the JSON when null so Spotflow ignores it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateDynamicAccountRequest(
        String currency,
        String accountName,
        long amount,
        Integer expiresIn
) {
}