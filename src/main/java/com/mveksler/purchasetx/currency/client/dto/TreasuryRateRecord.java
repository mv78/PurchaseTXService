// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.currency.client.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TreasuryRateRecord(
        String country,
        String currency,
        String countryCurrencyDesc,
        String exchangeRate,
        String effectiveDate,
        String recordDate
) {
}