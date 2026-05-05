// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.currency.client.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String EXCHANGE_RATES_CACHE = "exchangeRates";
}