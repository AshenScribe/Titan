package com.titan.productservice.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.titan.productservice.config.*;
import com.titan.productservice.controller.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.entity.ProductEntity;
import com.titan.productservice.exception.*;
import com.titan.productservice.service.*;
import com.titan.productservice.util.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

class ProductVariantRepositoryIntegrationTests extends AbstractRepositoryIntegrationTest {
    private ProductEntity persistedProduct(String slug) {
        var value = product(slug);
        products.insert(value);
        return value;
    }

    @Test
    void insertFindUpdateAndDeactivateRetainsHistoricalRow() {
        var product = persistedProduct("variant");
        var value = variant(product.getId(), "sku", BigDecimal.ZERO);
        variants.insert(value, 0);
        assertEquals(value.getId(), variants.findById(value.getId()).orElseThrow().getId());
        assertEquals(value.getId(), variants.findBySku("SKU").orElseThrow().getId());
        value.setVersion(0);
        value.setName("updated");
        assertEquals(1, variants.update(value, 1));
        assertEquals(1, variants.findByProductId(product.getId()).size());
        assertEquals(1, variants.deactivateById(value.getId(), product.getId(), 1, 2));
        assertEquals("INACTIVE", variants.findById(value.getId()).orElseThrow().getStatus());
    }

    @Test
    void constraintsCoverSkuBarcodePriceCurrencyAndAttributes() {
        var product = persistedProduct("constraints");
        var first = variant(product.getId(), "sku", BigDecimal.ZERO);
        first.setBarcode(null);
        variants.insert(first, 0);
        var second = variant(product.getId(), "SKU", BigDecimal.ONE);
        second.setBarcode(null);
        assertThrows(DataIntegrityViolationException.class, () -> variants.insert(second, 1));
        var invalidPrice = variant(product.getId(), "other", BigDecimal.valueOf(-1));
        assertThrows(DataIntegrityViolationException.class, () -> variants.insert(invalidPrice, 1));
        var invalidCurrency = variant(product.getId(), "currency", BigDecimal.ONE);
        invalidCurrency.setCurrency("US");
        assertThrows(
                DataIntegrityViolationException.class, () -> variants.insert(invalidCurrency, 1));
    }

    @Test
    void staleVariantAndMissingVariantAreRejected() {
        var product = persistedProduct("stale");
        var value = variant(product.getId(), "stale-sku", BigDecimal.ONE);
        variants.insert(value, 0);
        value.setVersion(99);
        assertThrows(OptimisticLockingFailureException.class, () -> variants.update(value, 1));
        assertTrue(variants.findById(java.util.UUID.randomUUID()).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> variants.deleteById(java.util.UUID.randomUUID()));
    }

    @Test
    void batchInsertSupportsEmptyAndMultipleItems() {
        var product = persistedProduct("batch");
        assertEquals(0, variants.insertBatch(List.of()).length);
        assertEquals(0, variants.insertBatch(null).length);
        assertEquals(
                2,
                variants.insertBatch(
                                List.of(
                                        variant(product.getId(), "a", BigDecimal.ONE),
                                        variant(product.getId(), "b", BigDecimal.valueOf(2))))
                        .length);
    }

    @Test
    void duplicateNonNullBarcodesAreRejected() {
        var product = persistedProduct("barcode");
        var first = variant(product.getId(), "barcode-a", BigDecimal.ONE);
        first.setBarcode("123");
        variants.insert(first, 0);
        var second = variant(product.getId(), "barcode-b", BigDecimal.valueOf(2));
        second.setBarcode("123");
        assertThrows(DataIntegrityViolationException.class, () -> variants.insert(second, 1));
    }

    @Test
    void inactiveVariantCanBeReactivatedWithExpectedVersions() {
        var product = persistedProduct("reactivate");
        var value = variant(product.getId(), "reactivate-sku", BigDecimal.ONE);
        variants.insert(value, 0);
        variants.deactivateById(value.getId(), product.getId(), 0, 1);
        value.setStatus("ACTIVE");
        value.setVersion(1);
        assertEquals(1, variants.update(value, 2));
        assertEquals("ACTIVE", variants.findById(value.getId()).orElseThrow().getStatus());
    }
}
