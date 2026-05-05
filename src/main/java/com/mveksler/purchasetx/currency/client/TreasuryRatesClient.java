// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.currency.client;

import com.mveksler.purchasetx.currency.client.domain.ExchangeRate;

import java.time.LocalDate;
import java.util.Optional;

public interface TreasuryRatesClient {

    Optional<ExchangeRate> findMostRecentRateOnOrBefore(
            String country,
            String currency,
            LocalDate onOrBeforeDate
    );
}