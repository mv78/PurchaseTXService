// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.currency.client.config;

import com.mveksler.purchasetx.currency.client.TreasuryApiProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class TreasuryClientConfig {

    @Bean
    public RestClient treasuryRestClient(TreasuryApiProperties properties) {
        var settings = HttpClientSettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
