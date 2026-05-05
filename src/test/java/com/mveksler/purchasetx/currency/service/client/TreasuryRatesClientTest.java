package com.mveksler.purchasetx.currency.service.client;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mveksler.purchasetx.currency.client.TreasuryRatesClient;
import com.mveksler.purchasetx.currency.client.domain.ExchangeRate;
import com.mveksler.purchasetx.error.exceptions.TreasuryApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TreasuryRatesClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();
    @Autowired
    private TreasuryRatesClient client;

    @DynamicPropertySource
    static void overrideBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("treasury.api.base-url", wireMock::baseUrl);
    }

    @Test
    void returnsExchangeRateWhenFound() {
        wireMock.stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country": "Canada",
                                      "currency": "Dollar",
                                      "country_currency_desc": "Canada-Dollar",
                                      "exchange_rate": "1.369",
                                      "effective_date": "2024-06-30",
                                      "record_date": "2024-06-30"
                                    }
                                  ],
                                  "meta": { "count": 1 }
                                }
                                """)));

        Optional<ExchangeRate> result = client.findMostRecentRateOnOrBefore(
                "Canada", "Dollar", LocalDate.of(2024, 8, 15));

        assertThat(result).isPresent();
        ExchangeRate rate = result.get();
        assertThat(rate.country()).isEqualTo("Canada");
        assertThat(rate.currency()).isEqualTo("Dollar");
        assertThat(rate.rate()).isEqualByComparingTo(new BigDecimal("1.369"));
        assertThat(rate.effectiveDate()).isEqualTo(LocalDate.of(2024, 6, 30));
    }

    @Test
    void returnsEmptyWhenNoDataFound() {
        wireMock.stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [],
                                  "meta": { "count": 0 }
                                }
                                """)));

        Optional<ExchangeRate> result = client.findMostRecentRateOnOrBefore(
                "Narnia", "Gold", LocalDate.of(2024, 8, 15));

        assertThat(result).isEmpty();
    }

    @Test
    void throwsTreasuryApiExceptionOnServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(500)));

        assertThatThrownBy(() -> client.findMostRecentRateOnOrBefore(
                "Canada", "Dollar", LocalDate.of(2024, 8, 15)))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionOnTimeout() {
        wireMock.stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withFixedDelay(10_000)
                        .withStatus(200)));

        assertThatThrownBy(() -> client.findMostRecentRateOnOrBefore(
                "Canada", "Dollar", LocalDate.of(2024, 8, 15)))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void throwsTreasuryApiExceptionOnMalformedJson() {
        wireMock.stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("not json at all")));

        assertThatThrownBy(() -> client.findMostRecentRateOnOrBefore(
                "Canada", "Dollar", LocalDate.of(2024, 8, 15)))
                .isInstanceOf(TreasuryApiException.class);
    }

    @Test
    void buildsCorrectQueryParameters() {
        wireMock.stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .withQueryParam("sort", equalTo("-effective_date"))
                .withQueryParam("page[size]", equalTo("1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "data": [], "meta": { "count": 0 } }
                                """)));

        client.findMostRecentRateOnOrBefore("Canada", "Dollar", LocalDate.of(2024, 8, 15));

        wireMock.verify(getRequestedFor(
                urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .withQueryParam("sort", equalTo("-effective_date"))
                .withQueryParam("page[size]", equalTo("1")));
    }

    @Test
    void handlesNullLiteralFieldsFromTreasury() {
        wireMock.stubFor(get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country": "Canada",
                                      "currency": "Dollar",
                                      "country_currency_desc": "Canada-Dollar",
                                      "exchange_rate": "1.369",
                                      "effective_date": "2024-06-30",
                                      "record_date": "null"
                                    }
                                  ],
                                  "meta": { "count": 1 }
                                }
                                """)));

        Optional<ExchangeRate> result = client.findMostRecentRateOnOrBefore(
                "Canada", "Dollar", LocalDate.of(2024, 8, 15));

        assertThat(result).isPresent();
        assertThat(result.get().effectiveDate()).isEqualTo(LocalDate.of(2024, 6, 30));
    }
}
