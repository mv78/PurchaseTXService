package com.mveksler.purchasetx.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TimezoneConfigurationTest {

    @Autowired
    private Clock clock;

    @Test
    void jvmDefaultTimezoneIsUtc() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }

    @Test
    void clockBeanUsesUtc() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}