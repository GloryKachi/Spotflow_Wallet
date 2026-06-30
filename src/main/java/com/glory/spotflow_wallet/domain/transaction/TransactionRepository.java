package com.glory.spotflow_wallet.domain.transaction;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReference(String reference);

    /**
     * Locks the transaction row for update so two concurrent webhook deliveries
     * for the same reference can't both pass the "still PENDING" check at once.
     * This is the core of the idempotency gate alongside the webhook_events table.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.reference = :reference")
    Optional<Transaction> findByReferenceForUpdate(String reference);

    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, LocalDateTime cutoff);
}
