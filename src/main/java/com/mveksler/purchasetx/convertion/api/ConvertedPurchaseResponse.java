// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.convertion.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Purchase transaction with currency conversion details")
public record ConvertedPurchaseResponse(
        @Schema(description = "Unique purchase ID") UUID id,
        @Schema(description = "Purchase description") String description,
        @Schema(description = "Transaction date (yyyy-MM-dd)", example = "2024-08-15") LocalDate transactionDate,
        @Schema(description = "Original amount in USD", example = "123.45") BigDecimal originalAmountUsd,
        @Schema(description = "Target country name", example = "Canada") String country,
        @Schema(description = "Target currency name", example = "Dollar") String currency,
        @Schema(description = "Exchange rate used for conversion", example = "1.369") BigDecimal exchangeRate,
        @Schema(description = "Date of the exchange rate used", example = "2024-06-30") LocalDate exchangeRateDate,
        @Schema(description = "Converted amount in target currency, rounded half-up to 2 decimal places", example = "169.00") BigDecimal convertedAmount
) {
}
