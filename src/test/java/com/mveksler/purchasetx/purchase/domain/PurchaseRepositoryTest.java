// Copyright (c) 2026 Mike Veksler. All rights reserved.
package com.mveksler.purchasetx.purchase.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PurchaseRepositoryTest {

    @Autowired
    private PurchaseRepository repository;

    /**
     * Verify  new record creation and find by id works
     * Verify project requirements:
     * Unique identifier: must uniquely identify the purchase
     */
    @Test
    void savesAndRetrievesPurchase() {
        Purchase purchase = new Purchase(
                "Office supplies",
                LocalDate.of(2024, 8, 15),
                new BigDecimal("123.45"),
                Instant.now()
        );

        Purchase saved = repository.save(purchase);
        assertThat(saved.getId()).isNotNull();

        Optional<Purchase> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).isEqualTo("Office supplies");
        assertThat(found.get().getTransactionDate()).isEqualTo(LocalDate.of(2024, 8, 15));
        assertThat(found.get().getPurchaseAmountUsd())
                .isEqualByComparingTo(new BigDecimal("123.45"));
    }

    /**
     * Verify JPA repository find by id works for unknown IDs
     */
    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<Purchase> found = repository.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    /**
     * Verify that data constraint is validated
     * Project requirement: Description: must not exceed 50 characters
     */
    @Test
    void rejectsDescriptionLongerThanFiftyCharacters() {
        // 51 characters — one over the limit
        String tooLong = "a".repeat(51);
        Purchase purchase = new Purchase(
                tooLong,
                LocalDate.of(2024, 8, 15),
                new BigDecimal("10.00"),
                Instant.now()
        );

        assertThatThrownBy(() -> repository.saveAndFlush(purchase))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Validate project requirement:
     * Purchase amount: must be a valid positive amount rounded to the nearest cent
     */
    @Test
    void rejectsNonPositiveAmount() {
        Purchase purchase = new Purchase(
                "Test",
                LocalDate.of(2024, 8, 15),
                new BigDecimal("0.00"),
                Instant.now()
        );

        assertThatThrownBy(() -> repository.saveAndFlush(purchase))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}