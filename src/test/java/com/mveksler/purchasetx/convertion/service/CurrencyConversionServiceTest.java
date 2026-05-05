package com.mveksler.purchasetx.convertion.service;

import com.mveksler.purchasetx.convertion.api.ConvertedPurchaseResponse;
import com.mveksler.purchasetx.currency.client.domain.ExchangeRate;
import com.mveksler.purchasetx.currency.client.service.ExchangeRateService;
import com.mveksler.purchasetx.error.exceptions.NoExchangeRateAvailableException;
import com.mveksler.purchasetx.error.exceptions.PurchaseNotFoundException;
import com.mveksler.purchasetx.purchase.domain.Purchase;
import com.mveksler.purchasetx.purchase.domain.PurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    private static final UUID PURCHASE_ID = UUID.randomUUID();
    private static final String COUNTRY = "Canada";
    private static final String CURRENCY = "Dollar";
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2024, 8, 15);
    private static final LocalDate RATE_DATE = LocalDate.of(2024, 6, 30);
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private ExchangeRateService exchangeRateService;
    private CurrencyConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = new CurrencyConversionService(purchaseRepository, exchangeRateService);
    }

    @Test
    void convertsAmountUsingExchangeRate() {
        Purchase purchase = purchaseWithAmount("123.45");
        ExchangeRate rate = rateWithValue("1.369");

        when(purchaseRepository.findById(PURCHASE_ID)).thenReturn(Optional.of(purchase));
        when(exchangeRateService.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.of(rate));

        ConvertedPurchaseResponse response = conversionService.convert(PURCHASE_ID, COUNTRY, CURRENCY);

        assertThat(response.convertedAmount()).isEqualByComparingTo("169.00");
        assertThat(response.exchangeRate()).isEqualByComparingTo("1.369");
        assertThat(response.exchangeRateDate()).isEqualTo(RATE_DATE);
        assertThat(response.originalAmountUsd()).isEqualByComparingTo("123.45");
    }

    @Test
    void roundsConvertedAmountHalfUp() {
        // 99.99 * 1.354 = 135.38646 → rounds to 135.39
        Purchase purchase = purchaseWithAmount("99.99");
        ExchangeRate rate = rateWithValue("1.354");

        when(purchaseRepository.findById(PURCHASE_ID)).thenReturn(Optional.of(purchase));
        when(exchangeRateService.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.of(rate));

        ConvertedPurchaseResponse response = conversionService.convert(PURCHASE_ID, COUNTRY, CURRENCY);

        assertThat(response.convertedAmount()).isEqualByComparingTo("135.39");
    }

    @Test
    void roundsDownWhenFractionBelowHalf() {
        // 10.00 * 1.333 = 13.33 → rounds to 13.33 (no change)
        Purchase purchase = purchaseWithAmount("10.00");
        ExchangeRate rate = rateWithValue("1.333");

        when(purchaseRepository.findById(PURCHASE_ID)).thenReturn(Optional.of(purchase));
        when(exchangeRateService.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.of(rate));

        ConvertedPurchaseResponse response = conversionService.convert(PURCHASE_ID, COUNTRY, CURRENCY);

        assertThat(response.convertedAmount()).isEqualByComparingTo("13.33");
    }

    @Test
    void throwsPurchaseNotFoundWhenPurchaseDoesNotExist() {
        when(purchaseRepository.findById(PURCHASE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.convert(PURCHASE_ID, COUNTRY, CURRENCY))
                .isInstanceOf(PurchaseNotFoundException.class);
    }

    @Test
    void throwsNoExchangeRateAvailableWhenNoEligibleRateExists() {
        Purchase purchase = purchaseWithAmount("100.00");

        when(purchaseRepository.findById(PURCHASE_ID)).thenReturn(Optional.of(purchase));
        when(exchangeRateService.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.convert(PURCHASE_ID, COUNTRY, CURRENCY))
                .isInstanceOf(NoExchangeRateAvailableException.class)
                .hasMessageContaining("Canada")
                .hasMessageContaining("Dollar");
    }

    @Test
    void responseIncludesAllRequiredFields() {
        Purchase purchase = purchaseWithAmount("50.00");
        ExchangeRate rate = rateWithValue("1.37");

        when(purchaseRepository.findById(PURCHASE_ID)).thenReturn(Optional.of(purchase));
        when(exchangeRateService.findEligibleRate(COUNTRY, CURRENCY, PURCHASE_DATE))
                .thenReturn(Optional.of(rate));

        ConvertedPurchaseResponse response = conversionService.convert(PURCHASE_ID, COUNTRY, CURRENCY);

        assertThat(response.id()).isEqualTo(PURCHASE_ID);
        assertThat(response.description()).isEqualTo("Office supplies");
        assertThat(response.transactionDate()).isEqualTo(PURCHASE_DATE);
        assertThat(response.originalAmountUsd()).isEqualByComparingTo("50.00");
        assertThat(response.country()).isEqualTo(COUNTRY);
        assertThat(response.currency()).isEqualTo(CURRENCY);
        assertThat(response.exchangeRate()).isEqualByComparingTo("1.37");
        assertThat(response.exchangeRateDate()).isEqualTo(RATE_DATE);
        assertThat(response.convertedAmount()).isNotNull();
    }

    // helpers

    private Purchase purchaseWithAmount(String amount) {
        Purchase purchase = new Purchase(
                "Office supplies",
                PURCHASE_DATE,
                new BigDecimal(amount),
                Instant.now()
        );
        ReflectionTestUtils.setField(purchase, "id", PURCHASE_ID);
        return purchase;
    }

    private ExchangeRate rateWithValue(String rate) {
        return new ExchangeRate(
                COUNTRY,
                CURRENCY,
                "Canada-Dollar",
                new BigDecimal(rate),
                RATE_DATE
        );
    }
}