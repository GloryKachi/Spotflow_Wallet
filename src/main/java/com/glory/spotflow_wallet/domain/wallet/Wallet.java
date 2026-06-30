package com.glory.spotflow_wallet.domain.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Used implicitly to guard against lost updates if two requests race;
     * the pessimistic lock used in WalletService is the primary defence,
     * this is a cheap secondary safety net. Maps to no new column since
     * the schema doesn't have one - so we deliberately do NOT use @Version
     * here to stay compatible with the existing migration. Concurrency is
     * instead handled via SELECT ... FOR UPDATE in WalletRepository.
     */

    protected Wallet() {
        // for JPA
    }

    public Wallet(Long userId, BigDecimal balance, String currency) {
        this.userId = userId;
        this.balance = balance;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void debit(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Wallet " + id + " has insufficient balance for debit of " + amount);
        }
        this.balance = this.balance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
