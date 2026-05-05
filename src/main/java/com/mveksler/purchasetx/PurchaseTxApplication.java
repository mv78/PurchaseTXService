// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PurchaseTxApplication {

    //Start application with UTC timezone
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(PurchaseTxApplication.class, args);
    }

}
