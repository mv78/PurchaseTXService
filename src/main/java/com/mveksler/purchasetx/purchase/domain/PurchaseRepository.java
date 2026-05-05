// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
}
