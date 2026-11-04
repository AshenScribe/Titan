package com.titan.productservice.repository;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.titan.productservice.entity.ProductEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

class ProductRepositoryIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Test
    void insertFindUpdateAndActivePagination() {
        ProductEntity value = product("draft");
        products.insert(value);
        assertEquals(value.getId(), products.findById(value.getId()).orElseThrow().getId());
        assertTrue(products.findActiveById(value.getId()).isEmpty());
        assertTrue(products.findIncludingDeletedById(value.getId()).isPresent());
        value.setStatus("INACTIVE");
        assertEquals(1, products.update(value));
        value.setVersion(1);
        value.setStatus("ACTIVE");
        value.setPublishedAt(OffsetDateTime.now());
        assertThrows(DataIntegrityViolationException.class, () -> products.update(value));
    }

    @Test
    void findByIdReturnsSoftDeletedProductsWhileActiveLookupExcludesThem() {
        var value = product("find-by-id-deleted");
        products.insert(value);
        assertTrue(products.softDeleteById(value.getId(), 0).isPresent());

        var found = products.findById(value.getId()).orElseThrow();
        assertEquals("INACTIVE", found.getStatus());
        assertNotNull(found.getDeletedAt());
        assertTrue(products.findActiveById(value.getId()).isEmpty());
    }

    @Test
    void optimisticUpdateAndSoftDeletePreserveExpectedVersionSemantics() {
        ProductEntity value = product("version");
        products.insert(value);
        value.setName("updated");
        assertEquals(1, products.update(value));
        assertEquals(1, productVersion(value.getId()));
        assertThrows(OptimisticLockingFailureException.class, () -> products.update(value));
        assertTrue(products.softDeleteById(value.getId(), 1).isPresent());
        assertTrue(products.findActiveById(value.getId()).isEmpty());
        assertTrue(products.findIncludingDeletedById(value.getId()).isPresent());
        assertTrue(products.softDeleteById(value.getId(), 1).isEmpty());
        assertEquals(2, productVersion(value.getId()));
    }

    @Test
    void duplicateSlugInvalidBrandAndInvalidStatusAreRejected() {
        products.insert(product("duplicate"));
        assertThrows(
                DataIntegrityViolationException.class, () -> products.insert(product("DUPLICATE")));
        ProductEntity invalidBrand = product("invalid-brand");
        invalidBrand.setBrandId(UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class, () -> products.insert(invalidBrand));
        ProductEntity invalidStatus = product("invalid-status");
        invalidStatus.setStatus("BROKEN");
        assertThrows(DataIntegrityViolationException.class, () -> products.insert(invalidStatus));
    }

    @Test
    void batchInsertEmptyAndMultipleAreSupported() {
        assertEquals(0, products.insertBatch(java.util.List.of()).length);
        assertEquals(0, products.insertBatch(null).length);
        assertEquals(
                2, products.insertBatch(java.util.List.of(product("one"), product("two"))).length);
    }

    @Test
    void paginationRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> products.findActive(0, 0));
        assertThrows(IllegalArgumentException.class, () -> products.findActive(10, -1));
    }
}
