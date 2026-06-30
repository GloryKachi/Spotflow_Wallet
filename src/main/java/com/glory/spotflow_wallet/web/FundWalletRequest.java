package com.glory.spotflow_wallet.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FundWalletRequest(
        @NotNull Long userId,
        @NotNull @Positive Double amount
) {
}
