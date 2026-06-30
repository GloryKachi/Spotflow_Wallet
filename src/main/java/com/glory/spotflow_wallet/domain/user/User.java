package com.glory.spotflow_wallet.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "bank_account_number", nullable = false)
    private String bankAccountNumber;

    @Column(name = "bank_code", nullable = false)
    private String bankCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected User() {
        // for JPA
    }

    public User(String fullName, String bankAccountNumber, String bankCode) {
        this.fullName = fullName;
        this.bankAccountNumber = bankAccountNumber;
        this.bankCode = bankCode;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public String getBankCode() {
        return bankCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
