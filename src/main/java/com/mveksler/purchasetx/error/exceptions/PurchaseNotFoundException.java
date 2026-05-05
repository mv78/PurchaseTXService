// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.error.exceptions;

import java.util.UUID;

public class PurchaseNotFoundException extends RuntimeException {
    private final UUID purchaseId;

    public PurchaseNotFoundException(UUID purchaseId) {
        super("Purchase not found: " + purchaseId);
        this.purchaseId = purchaseId;
    }

    public UUID getPurchaseId() {
        return purchaseId;
    }
}
