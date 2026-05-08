// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    @Query("SELECT SUM(p.purchaseAmountUsd) FROM Purchase p WHERE EXTRACT(YEAR FROM p.transactionDate) = :year AND EXTRACT(MONTH FROM p.transactionDate) = :month")
    BigDecimal sumByYearAndMonth(@Param("year") int year, @Param("month") int month);
}
