package com.mveksler.purchasetx.currency.service;

import com.mveksler.purchasetx.currency.client.TreasuryRatesClient;
import com.mveksler.purchasetx.currency.client.domain.ExchangeRate;
import com.mveksler.purchasetx.currency.client.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final String COUNTRY = "Canada";
    private static final String CURRENCY = "Dollar";
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2024, 8, 15);
    @Mock
    private TreasuryRatesClient client;
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(client);
    }

    @Test
    void returnsRateWhenWithinSixMonths() {
        LocalDate rateDate = LocalDate.of(2024, 6, 30); // 46 days before purchase
        ExchangeRate rate = rate(rateDate);

        when(client.findMostRecentRateOnOrBefore(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.of(rate));

        Optional<ExchangeRate> result = service.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE);

        assertThat(result).isPresent();
        assertThat(result.get().effectiveDate()).isEqualTo(rateDate);
    }

    @Test
    void returnsRateWhenEffectiveDateIsExactlyPurchaseDate() {
        ExchangeRate rate = rate(PURCHASE_DATE);

        when(client.findMostRecentRateOnOrBefore(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.of(rate));

        assertThat(service.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE)).isPresent();
    }

    @Test
    void returnsRateWhenEffectiveDateIsExactlySixMonthsBefore() {
        // Boundary: exactly 6 months before should be eligible
        LocalDate exactBoundary = PURCHASE_DATE.minusMonths(6);
        ExchangeRate rate = rate(exactBoundary);

        when(client.findMostRecentRateOnOrBefore(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.of(rate));

        assertThat(service.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE)).isPresent();
    }

    @Test
    void returnsEmptyWhenRateIsOlderThanSixMonths() {
        // One day beyond the boundary — should be ineligible
        LocalDate tooOld = PURCHASE_DATE.minusMonths(6).minusDays(1);
        ExchangeRate rate = rate(tooOld);

        when(client.findMostRecentRateOnOrBefore(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.of(rate));

        assertThat(service.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoRateExistsAtAll() {
        when(client.findMostRecentRateOnOrBefore(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.empty());

        assertThat(service.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE)).isEmpty();
    }

    private ExchangeRate rate(LocalDate effectiveDate) {
        return new ExchangeRate(
                COUNTRY, CURRENCY, "Canada-Dollar",
                new BigDecimal("1.369"), effectiveDate
        );
    }
}