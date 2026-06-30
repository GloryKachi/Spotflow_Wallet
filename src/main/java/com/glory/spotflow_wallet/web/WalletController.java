package com.glory.spotflow_wallet.web;

import com.glory.spotflow_wallet.domain.wallet.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/fund")
    public ResponseEntity<FundWalletResponse> fund(@Valid @RequestBody FundWalletRequest request) {
        WalletService.FundResult result = walletService.fundWallet(request.userId(), BigDecimal.valueOf(request.amount()));

        FundWalletResponse response = new FundWalletResponse(
                result.transaction().getReference(),
                result.transaction().getStatus().name(),
                result.virtualAccount().accountNumber(),
                result.virtualAccount().bankName(),
                result.virtualAccount().accountName()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        WalletService.WithdrawResult result = walletService.withdraw(
                request.userId(),
                BigDecimal.valueOf(request.amount()),
                request.bankAccountNumber(),
                request.bankCode(),
                request.accountName()
        );

        WithdrawResponse response = new WithdrawResponse(
                result.transaction().getReference(),
                result.transaction().getStatus().name(),
                result.newBalance()
        );
        return ResponseEntity.ok(response);
    }
}
