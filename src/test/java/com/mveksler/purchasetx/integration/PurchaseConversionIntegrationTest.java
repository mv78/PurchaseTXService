// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.mveksler.purchasetx.currency.client.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseConversionIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CacheManager cacheManager;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("treasury.api.base-url", wireMock::baseUrl);
    }

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("DELETE FROM purchase");
        wireMock.resetAll();
        cacheManager.getCache(CacheConfig.EXCHANGE_RATES_CACHE).clear();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String createPurchase(String description, String date, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "%s",
                                  "transactionDate": "%s",
                                  "purchaseAmountUsd": %s
                                }
                                """.formatted(description, date, amount)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void stubTreasuryRate(String country, String currency, String rate, String effectiveDate) {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "data": [
                                    {
                                      "country": "%s",
                                      "currency": "%s",
                                      "country_currency_desc": "%s-%s",
                                      "exchange_rate": "%s",
                                      "effective_date": "%s",
                                      "record_date": "%s"
                                    }
                                  ],
                                  "meta": { "count": 1 }
                                }
                                """.formatted(country, currency, country, currency, rate, effectiveDate, effectiveDate))));
    }

    private void stubTreasuryEmpty() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "data": [], "meta": { "count": 0 } }
                                """)));
    }

    private void stubTreasuryError() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/services/api/fiscal_service/v1/accounting/od/rates_of_exchange"))
                .willReturn(aResponse().withStatus(500)));
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void createAndRetrievePurchase() throws Exception {
        String id = createPurchase("Office supplies", "2024-08-15", "123.45");

        mockMvc.perform(get("/api/v1/purchases/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.transactionDate").value("2024-08-15"))
                .andExpect(jsonPath("$.purchaseAmountUsd").value(123.45));
    }

    @Test
    void endToEndConversionHappyPath() throws Exception {
        String id = createPurchase("Office supplies", "2024-08-15", "123.45");
        stubTreasuryRate("Canada", "Dollar", "1.369", "2024-06-30");

        mockMvc.perform(get("/api/v1/purchases/{id}/converted", id)
                        .param("country", "Canada")
                        .param("currency", "Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.description").value("Office supplies"))
                .andExpect(jsonPath("$.transactionDate").value("2024-08-15"))
                .andExpect(jsonPath("$.originalAmountUsd").value(123.45))
                .andExpect(jsonPath("$.country").value("Canada"))
                .andExpect(jsonPath("$.currency").value("Dollar"))
                .andExpect(jsonPath("$.exchangeRate").exists())
                .andExpect(jsonPath("$.exchangeRateDate").value("2024-06-30"))
                .andExpect(jsonPath("$.convertedAmount").value(169.00));
    }

    @Test
    void conversionMathIsCorrect() throws Exception {
        // 99.99 * 1.354 = 135.38646 → rounds HALF_UP to 135.39
        String id = createPurchase("Test rounding", "2024-08-15", "99.99");
        stubTreasuryRate("Canada", "Dollar", "1.354", "2024-06-30");

        mockMvc.perform(get("/api/v1/purchases/{id}/converted", id)
                        .param("country", "Canada")
                        .param("currency", "Dollar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(135.39));
    }

    @Test
    void returns404ForUnknownPurchase() throws Exception {
        mockMvc.perform(get("/api/v1/purchases/00000000-0000-0000-0000-000000000000/converted")
                        .param("country", "Canada")
                        .param("currency", "Dollar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Purchase not found"));
    }

    @Test
    void returns422WhenNoRateAvailable() throws Exception {
        String id = createPurchase("Old purchase", "2024-08-15", "100.00");
        stubTreasuryEmpty();

        mockMvc.perform(get("/api/v1/purchases/{id}/converted", id)
                        .param("country", "Canada")
                        .param("currency", "Dollar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Exchange rate not available"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void returns422WhenRateIsOlderThanSixMonths() throws Exception {
        String id = createPurchase("Purchase", "2024-08-15", "100.00");
        // Rate from 2023-12-31 is more than 6 months before 2024-08-15
        // 6 months before 2024-08-15 = 2024-02-15
        // 2023-12-31 < 2024-02-15 → too old
        stubTreasuryRate("Canada", "Dollar", "1.350", "2023-12-31");

        mockMvc.perform(get("/api/v1/purchases/{id}/converted", id)
                        .param("country", "Canada")
                        .param("currency", "Dollar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Exchange rate not available"));
    }

    @Test
    void returns502WhenTreasuryIsDown() throws Exception {
        String id = createPurchase("Purchase", "2024-08-15", "100.00");
        stubTreasuryError();

        mockMvc.perform(get("/api/v1/purchases/{id}/converted", id)
                        .param("country", "Canada")
                        .param("currency", "Dollar"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Upstream service unavailable"));
    }

    @Test
    void returns400WhenCountryParamMissing() throws Exception {
        String id = createPurchase("Purchase", "2024-08-15", "100.00");

        mockMvc.perform(get("/api/v1/purchases/{id}/converted", id)
                        .param("currency", "Dollar"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenCurrencyParamMissing() throws Exception {
        String id = createPurchase("Purchase", "2024-08-15", "100.00");

        mockMvc.perform(get("/api/v1/purchases/{id}/converted", id)
                        .param("country", "Canada"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correlationIdIsReturnedInResponseHeader() throws Exception {
        mockMvc.perform(get("/api/v1/purchases/00000000-0000-0000-0000-000000000000")
                        .header("X-Correlation-ID", "test-trace-123"))
                .andExpect(header().string("X-Correlation-ID", "test-trace-123"));
    }

    @Test
    void correlationIdIsGeneratedWhenNotProvided() throws Exception {
        mockMvc.perform(get("/api/v1/purchases/00000000-0000-0000-0000-000000000000"))
                .andExpect(header().exists("X-Correlation-ID"));
    }
}