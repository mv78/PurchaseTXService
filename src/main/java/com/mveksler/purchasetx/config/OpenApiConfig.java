// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI purchaseTxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PurchaseTx API")
                        .description("Store purchase transactions and retrieve them converted to a target currency using US Treasury exchange rates.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Mike Veksler")
                                .email("mveksler@gmail.com")));
    }
}
