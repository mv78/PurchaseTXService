// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.convertion.api;

import com.mveksler.purchasetx.convertion.service.CurrencyConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/purchases")
@Tag(name = "Purchases", description = "Create and retrieve purchase transactions")
public class ConversionController {

    private final CurrencyConversionService conversionService;

    public ConversionController(CurrencyConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Operation(
            summary = "Get a purchase converted to a target currency",
            description = "Converts the stored USD amount using the most recent US Treasury exchange rate " +
                    "on or before the purchase date. The rate must be within 6 months of the purchase date.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Conversion successful"),
                    @ApiResponse(responseCode = "400", description = "Missing or invalid parameters",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "404", description = "Purchase not found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "422", description = "No eligible exchange rate available within 6 months of the purchase date",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "502", description = "US Treasury API is unavailable",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class)))
            }
    )
    @GetMapping(
            value = "/{id}/converted",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ConvertedPurchaseResponse convert(
            @PathVariable UUID id,
            @Parameter(description = "Country name as used by the US Treasury (e.g. Canada, Japan)", required = true)
            @RequestParam @NotBlank(message = "Country is required") String country,
            @Parameter(description = "Currency name as used by the US Treasury (e.g. Dollar, Yen)", required = true)
            @RequestParam @NotBlank(message = "Currency is required") String currency
    ) {
        return conversionService.convert(id, country, currency);
    }
}
