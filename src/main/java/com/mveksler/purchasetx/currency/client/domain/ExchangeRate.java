// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.currency.client.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRate(
        String country,
        String currency,
        String countryCurrencyDesc,
        BigDecimal rate,
        LocalDate effectiveDate
) {
}