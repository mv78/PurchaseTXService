// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class UTCTimeConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
