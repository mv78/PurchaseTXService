// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.convertion.service;

import com.mveksler.purchasetx.convertion.api.ConvertedPurchaseResponse;
import com.mveksler.purchasetx.currency.client.domain.ExchangeRate;
import com.mveksler.purchasetx.currency.client.service.ExchangeRateService;
import com.mveksler.purchasetx.error.exceptions.NoExchangeRateAvailableException;
import com.mveksler.purchasetx.error.exceptions.PurchaseNotFoundException;
import com.mveksler.purchasetx.purchase.domain.Purchase;
import com.mveksler.purchasetx.purchase.domain.PurchaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CurrencyConversionService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyConversionService.class);

    private final PurchaseRepository purchaseRepository;
    private final ExchangeRateService exchangeRateService;

    public CurrencyConversionService(PurchaseRepository purchaseRepository, ExchangeRateService exchangeRateService) {
        this.purchaseRepository = purchaseRepository;
        this.exchangeRateService = exchangeRateService;
    }

    public ConvertedPurchaseResponse convert(UUID purchaseId, String country, String currency) {
        log.info("Converting purchase {} to {}-{}", purchaseId, country, currency);

        var purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new PurchaseNotFoundException(purchaseId));

        ExchangeRate rate = exchangeRateService
                .findEligibleRate(country, currency, purchase.getTransactionDate())
                .orElseThrow(() -> new NoExchangeRateAvailableException(
                        purchaseId, country, currency, purchase.getTransactionDate()));

        BigDecimal converted = purchase.getPurchaseAmountUsd()
                .multiply(rate.rate())
                .setScale(2, RoundingMode.HALF_UP);

        return new ConvertedPurchaseResponse(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getTransactionDate(),
                purchase.getPurchaseAmountUsd(),
                country,
                currency,
                rate.rate(),
                rate.effectiveDate(),
                converted
        );
    }
}
