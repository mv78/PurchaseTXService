// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.error.exceptions;

import java.time.LocalDate;
import java.util.UUID;

public class NoExchangeRateAvailableException extends RuntimeException {

    private final UUID purchaseId;
    private final String country;
    private final String currency;
    private final LocalDate purchaseDate;

    public NoExchangeRateAvailableException(
            UUID purchaseId,
            String country,
            String currency,
            LocalDate purchaseDate
    ) {
        super(String.format(
                "No exchange rate available for %s-%s within 6 months on or before %s",
                country, currency, purchaseDate
        ));
        this.purchaseId = purchaseId;
        this.country = country;
        this.currency = currency;
        this.purchaseDate = purchaseDate;
    }

    public UUID getPurchaseId() {
        return purchaseId;
    }

    public String getCountry() {
        return country;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }
}