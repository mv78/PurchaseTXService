// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.api;


import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Custom date field deserializer to check for date format
 * Expected ISO-8601 (yyyy-MM-dd)
 */
public class StrictLocalDateDeserializer extends ValueDeserializer<LocalDate> {

    private static final DateTimeFormatter STRICT_ISO_DATE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd")
                    .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public Class<LocalDate> handledType() {
        return LocalDate.class;
    }

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext ctxt) {
        String value = parser.getString();
        if (parser.hasToken(JsonToken.VALUE_NULL) | value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value, STRICT_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw ctxt.weirdStringException(
                    value,
                    LocalDate.class,
                    "Invalid date format. Expected ISO-8601 (yyyy-MM-dd)."
            );
        }
    }
}