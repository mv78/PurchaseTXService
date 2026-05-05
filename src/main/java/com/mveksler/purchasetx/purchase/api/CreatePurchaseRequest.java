// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

// transactionDate must be ISO-8601 (yyyy-MM-dd) — US-style and datetime formats are rejected
@Schema(description = "Request body for creating a purchase transaction")
public record CreatePurchaseRequest(
        @Schema(description = "Purchase description", example = "Office supplies", maxLength = 50)
        @NotBlank(message = "Description is required")
        @Size(max = 50, message = "Description must not exceed 50 characters")
        String description,

        @Schema(description = "Date of the transaction in ISO-8601 format (yyyy-MM-dd)", example = "2024-08-15")
        @NotNull(message = "Transaction date is required")
        @PastOrPresent(message = "Transaction date cannot be in the future")
        @JsonDeserialize(using = StrictLocalDateDeserializer.class)
        LocalDate transactionDate,

        @Schema(description = "Purchase amount in USD, max 2 decimal places", example = "123.45")
        @NotNull(message = "Purchase amount is required")
        @DecimalMin(value = "0.01", message = "Purchase amount must be positive")
        @Digits(integer = 13, fraction = 2, message = "Purchase amount must have at most 2 decimal places")
        BigDecimal purchaseAmountUsd
) {
}
