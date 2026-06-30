package com.glory.spotflow_wallet.web;

import java.math.BigDecimal;

public record WithdrawResponse(
        String reference,
        String status,
        BigDecimal newBalance
) {
}
