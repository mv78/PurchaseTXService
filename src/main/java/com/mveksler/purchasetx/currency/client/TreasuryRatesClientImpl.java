// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.currency.client;

import com.mveksler.purchasetx.currency.client.domain.ExchangeRate;
import com.mveksler.purchasetx.currency.client.dto.TreasuryRateRecord;
import com.mveksler.purchasetx.currency.client.dto.TreasuryRateResponse;
import com.mveksler.purchasetx.error.exceptions.TreasuryApiException;
import com.mveksler.purchasetx.error.exceptions.TransientTreasuryApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class TreasuryRatesClientImpl implements TreasuryRatesClient {

    private static final Logger log = LoggerFactory.getLogger(TreasuryRatesClientImpl.class);

    private static final String RATES_PATH =
            "/services/api/fiscal_service/v1/accounting/od/rates_of_exchange";
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_DELAY_MS = 500;

    private final RestClient restClient;

    public TreasuryRatesClientImpl(RestClient treasuryRestClient) {
        this.restClient = treasuryRestClient;
    }

    @Override
    public Optional<ExchangeRate> findMostRecentRateOnOrBefore(
            String country,
            String currency,
            LocalDate onOrBeforeDate
    ) {
        int attempt = 0;
        long delayMs = INITIAL_DELAY_MS;

        while (true) {
            attempt++;
            try {
                return doFetch(country, currency, onOrBeforeDate);
            } catch (TransientTreasuryApiException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                log.warn("Treasury API attempt {}/{} failed — retrying in {}ms: {}",
                        attempt, MAX_ATTEMPTS, delayMs, ex.getMessage());
                sleep(delayMs);
                delayMs *= 2;
            }
        }
    }

    private Optional<ExchangeRate> doFetch(String country, String currency, LocalDate onOrBeforeDate) {
        log.info("Fetching exchange rate for {}-{} on or before {}", country, currency, onOrBeforeDate);

        try {
            TreasuryRateResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(RATES_PATH)
                            .queryParam("fields", "country,currency,country_currency_desc,exchange_rate,effective_date,record_date")
                            .queryParam("filter", buildFilter(country, currency, onOrBeforeDate))
                            .queryParam("sort", "-effective_date")
                            .queryParam("page[size]", "1")
                            .build())
                    .retrieve()
                    .body(TreasuryRateResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                log.debug("No rate found for {}-{} on or before {}", country, currency, onOrBeforeDate);
                return Optional.empty();
            }

            return Optional.of(toExchangeRate(response.data().get(0)));

        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                log.warn("Treasury API returned {} — will retry", ex.getStatusCode());
                throw new TransientTreasuryApiException(
                        "Treasury API returned error: " + ex.getStatusCode(), ex);
            }
            log.error("Treasury API returned non-retriable error {}: {}", ex.getStatusCode(), ex.getMessage());
            throw new TreasuryApiException(
                    "Treasury API returned error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("Treasury API request failed (timeout or connection error) — will retry: {}", ex.getMessage());
            throw new TransientTreasuryApiException(
                    "Treasury API is unavailable", ex);
        } catch (RestClientException ex) {
            log.error("Treasury API response could not be processed: {}", ex.getMessage());
            throw new TreasuryApiException(
                    "Treasury API response could not be processed", ex);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TreasuryApiException("Retry interrupted", ie);
        }
    }

    private String buildFilter(String country, String currency, LocalDate onOrBeforeDate) {
        return String.format(
                "country:eq:%s,currency:eq:%s,effective_date:lte:%s",
                country, currency, onOrBeforeDate
        );
    }

    private ExchangeRate toExchangeRate(TreasuryRateRecord record) {
        return new ExchangeRate(
                record.country(),
                record.currency(),
                record.countryCurrencyDesc(),
                parseRate(record.exchangeRate()),
                parseDate(record.effectiveDate())
        );
    }

    private BigDecimal parseRate(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return LocalDate.parse(value);
    }
}
