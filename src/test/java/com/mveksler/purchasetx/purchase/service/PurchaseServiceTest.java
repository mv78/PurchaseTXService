// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.service;

import com.mveksler.purchasetx.error.exceptions.PurchaseNotFoundException;
import com.mveksler.purchasetx.purchase.api.CreatePurchaseRequest;
import com.mveksler.purchasetx.purchase.domain.Purchase;
import com.mveksler.purchasetx.purchase.domain.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class PurchaseServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2024-08-15T12:00:00Z"), ZoneOffset.UTC);
    @Mock
    private PurchaseRepository repository;

    @Test
    void createPersistsPurchaseWithCurrentTimestamp() {
        var service = new PurchaseService(repository, fixedClock);
        var request = new CreatePurchaseRequest(
                "Office supplies", LocalDate.of(2024, 8, 15), new BigDecimal("123.45")
        );
        when(repository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        Purchase result = service.create(request);

        assertThat(result.getDescription()).isEqualTo("Office supplies");
        assertThat(result.getCreatedAt()).isEqualTo(Instant.parse("2024-08-15T12:00:00Z"));
        verify(repository).save(any(Purchase.class));
    }

    @Test
    void findByIdThrowsWhenNotPresent() {
        var service = new PurchaseService(repository, fixedClock);
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(PurchaseNotFoundException.class);
    }
}
