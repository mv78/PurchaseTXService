// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.api;

import com.mveksler.purchasetx.purchase.domain.Purchase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Stored purchase transaction")
public record PurchaseResponse(
        @Schema(description = "Unique purchase ID") UUID id,
        @Schema(description = "Purchase description") String description,
        @Schema(description = "Transaction date (yyyy-MM-dd)", example = "2024-08-15") LocalDate transactionDate,
        @Schema(description = "Original amount in USD", example = "123.45") BigDecimal purchaseAmountUsd
) {
    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getTransactionDate(),
                purchase.getPurchaseAmountUsd()
        );
    }
}
