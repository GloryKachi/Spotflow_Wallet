package com.glory.spotflow_wallet.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WithdrawRequest(
        @NotNull Long userId,
        @NotNull @Positive Double amount,
        @NotBlank String bankAccountNumber,
        @NotBlank String bankCode,
        @NotBlank String accountName
) {
}
