package com.glory.spotflow_wallet.web;

public record FundWalletResponse(
        String reference,
        String status,
        String virtualAccountNumber,
        String bankName,
        String accountName
) {
}
