// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.currency.client.service;

import com.mveksler.purchasetx.currency.client.TreasuryRatesClient;
import com.mveksler.purchasetx.currency.client.config.CacheConfig;
import com.mveksler.purchasetx.currency.client.domain.ExchangeRate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final int LOOKBACK_MONTHS = 6;

    private final TreasuryRatesClient client;

    public ExchangeRateService(TreasuryRatesClient client) {
        this.client = client;
    }

    @Cacheable(
            value = CacheConfig.EXCHANGE_RATES_CACHE,
            key = "#country + '-' + #currency + '-' + #purchaseDate"
    )
    public Optional<ExchangeRate> findEligibleRate(String country, String currency, LocalDate purchaseDate) {
        var cutoff = purchaseDate.minusMonths(LOOKBACK_MONTHS);
        var rate = client.findMostRecentRateOnOrBefore(country, currency, purchaseDate);

        if (rate.isEmpty()) {
            return Optional.empty();
        }

        ExchangeRate found = rate.get();
        if (found.effectiveDate().isBefore(cutoff)) {
            log.info("Rate for {}-{} found but too old (effective {}, cutoff {})",
                    country, currency, found.effectiveDate(), cutoff);
            return Optional.empty();
        }

        log.info("Using rate {}/{} effective {} for {}-{}", found.rate(), found.effectiveDate(), purchaseDate, country, currency);
        return rate;
    }
}
