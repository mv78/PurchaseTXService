// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.currency.client.dto;

import java.util.List;

public record TreasuryRateResponse(
        List<TreasuryRateRecord> data
) {
}
