// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "PURCHASE")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "DESCRIPTION", nullable = false, length = 50)
    private String description;

    @Column(name = "TRANSACTION_DATE", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "PURCHASE_AMOUNT_USD", nullable = false, precision = 15, scale = 2)
    private BigDecimal purchaseAmountUsd;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    protected Purchase() {
    }

    public Purchase(String description, LocalDate transactionDate, BigDecimal purchaseAmountUsd, Instant createdAt) {
        this.description = description;
        this.transactionDate = transactionDate;
        this.purchaseAmountUsd = purchaseAmountUsd;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public BigDecimal getPurchaseAmountUsd() { return purchaseAmountUsd; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "Purchase{id=" + id + ", transactionDate=" + transactionDate + "}";
    }
}
