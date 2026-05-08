package com.mveksler.purchasetx.purchase.api;

import java.math.BigDecimal;

public record PurchaseSumResponse(BigDecimal totalAmount) {
    static PurchaseSumResponse from(BigDecimal totalAmount) {
        return new PurchaseSumResponse(totalAmount);
    }
}
