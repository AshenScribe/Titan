package com.titan.productservice.integration;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.titan.productservice.repository.AbstractRepositoryIntegrationTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

class ProductAggregateInvariantIntegrationTests extends AbstractRepositoryIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private TransactionTemplate transactions;

    @Test
    void draftProductMayExistWithoutVariants() {
        var product = product("draft-without-variants");
        products.insert(product);
        assertEquals(
                "DRAFT",
                products.findIncludingDeletedById(product.getId()).orElseThrow().getStatus());
    }

    @Test
    void activatingWithoutActiveVariantFailsAtCommitAndRollsBack() {
        var product = product("activate-empty");
        products.insert(product);
        product.setStatus("ACTIVE");
        product.setPublishedAt(OffsetDateTime.now());
        assertThrows(DataAccessException.class, () -> products.update(product));
        assertEquals(
                "DRAFT",
                products.findIncludingDeletedById(product.getId()).orElseThrow().getStatus());
    }

    @Test
    void activeProductCanHaveOneOrMoreActiveVariants() {
        var product = product("active-with-variants");
        products.insert(product);
        var variant = variant(product.getId(), "active-one", BigDecimal.ONE);
        variants.insert(variant, 0);
        product.setStatus("ACTIVE");
        product.setPublishedAt(OffsetDateTime.now());
        product.setVersion(1);
        products.update(product);
        assertTrue(products.findActiveById(product.getId()).isPresent());
        var second = variant(product.getId(), "active-two", BigDecimal.valueOf(2));
        variants.insert(second, 2);
        assertEquals(2, variants.findByProductId(product.getId()).size());
    }

    @Test
    void deactivatingOnlyActiveVariantFailsButDeactivatingOneOfTwoSucceeds() {
        var product = product("deactivate-invariant");
        products.insert(product);
        var first = variant(product.getId(), "first", BigDecimal.ONE);
        variants.insert(first, 0);
        product.setStatus("ACTIVE");
        product.setPublishedAt(OffsetDateTime.now());
        product.setVersion(1);
        products.update(product);
        assertThrows(
                DataAccessException.class,
                () -> variants.deactivateById(first.getId(), product.getId(), 0, 2));
        assertEquals("ACTIVE", variants.findById(first.getId()).orElseThrow().getStatus());

        var second = variant(product.getId(), "second", BigDecimal.valueOf(2));
        variants.insert(second, 2);
        assertEquals(1, variants.deactivateById(first.getId(), product.getId(), 0, 3));
        assertEquals("INACTIVE", variants.findById(first.getId()).orElseThrow().getStatus());
        assertEquals("ACTIVE", variants.findById(second.getId()).orElseThrow().getStatus());
    }

    @Test
    void firstVariantAndProductCreationCanCommitAsOneTransaction() {
        var product = product("atomic-create");
        var variant = variant(product.getId(), "atomic-sku", BigDecimal.ONE);
        transactions.executeWithoutResult(
                status -> {
                    products.insert(product);
                    variants.insert(variant, 0);
                    product.setStatus("ACTIVE");
                    product.setPublishedAt(OffsetDateTime.now());
                    product.setVersion(1);
                    products.update(product);
                });
        assertTrue(products.findActiveById(product.getId()).isPresent());
        assertEquals(1, variants.findByProductId(product.getId()).size());
    }
}
