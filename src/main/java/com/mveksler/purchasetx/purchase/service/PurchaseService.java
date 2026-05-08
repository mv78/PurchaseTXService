// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.service;

import com.mveksler.purchasetx.error.exceptions.PurchaseNotFoundException;
import com.mveksler.purchasetx.purchase.api.CreatePurchaseRequest;
import com.mveksler.purchasetx.purchase.domain.Purchase;
import com.mveksler.purchasetx.purchase.domain.PurchaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final PurchaseRepository repository;
    private final Clock clock;

    public PurchaseService(PurchaseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Purchase create(CreatePurchaseRequest request) {
        Purchase purchase = new Purchase(
                request.description(),
                request.transactionDate(),
                request.purchaseAmountUsd(),
                Instant.now(clock)
        );
        Purchase saved = repository.save(purchase);
        log.info("Created purchase id={} date={} amount={}", saved.getId(), saved.getTransactionDate(), saved.getPurchaseAmountUsd());
        return saved;
    }

    public Purchase findById(UUID id) {
        log.info("Fetching purchase id={}", id);
        return repository.findById(id)
                .orElseThrow(() -> new PurchaseNotFoundException(id));
    }

    public BigDecimal sumByMonth(YearMonth month) {
        BigDecimal result = repository.sumByYearAndMonth(month.getYear(), month.getMonthValue());
        return result != null ? result : BigDecimal.ZERO;
    }
}