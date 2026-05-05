// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.api;

import com.mveksler.purchasetx.purchase.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/purchases")
@Tag(name = "Purchases", description = "Create and retrieve purchase transactions")
public class PurchaseController {

    private final PurchaseService service;

    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    @Operation(
            summary = "Create a purchase",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Purchase created",
                            headers = @Header(name = "Location", description = "URL of the created purchase")),
                    @ApiResponse(responseCode = "400", description = "Validation error or malformed request body",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class)))
            }
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PurchaseResponse> createPurchase(
            @Valid @RequestBody CreatePurchaseRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        var purchase = service.create(request);
        var response = PurchaseResponse.from(purchase);
        URI location = uriBuilder
                .path("/api/v1/purchases/{id}")
                .buildAndExpand(purchase.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(
            summary = "Get a purchase by ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Purchase found"),
                    @ApiResponse(responseCode = "400", description = "ID is not a valid UUID",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "404", description = "Purchase not found",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class)))
            }
    )
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PurchaseResponse findPurchaseById(@PathVariable UUID id) {
        return PurchaseResponse.from(service.findById(id));
    }
}
