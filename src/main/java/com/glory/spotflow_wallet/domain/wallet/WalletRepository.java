package com.glory.spotflow_wallet.domain.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    /**
     * Row-level lock so two concurrent webhook deliveries (or a withdraw + a credit)
     * for the same user's wallet can't both read a stale balance and clobber each other.
     * Used by WalletService whenever balance is mutated.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(Long userId);
}
