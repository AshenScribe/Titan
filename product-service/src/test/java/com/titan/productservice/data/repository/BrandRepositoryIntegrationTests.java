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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.titan.productservice.entity.BrandEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class BrandRepositoryIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Test
    void insertFindUpdateActivePaginationAndDelete() {
        BrandEntity first = brand("first");
        BrandEntity second = brand("second");
        brands.insert(first);
        brands.insert(second);
        assertEquals(first.getId(), brands.findById(first.getId()).orElseThrow().getId());
        assertEquals(first.getId(), brands.findBySlug("FIRST").orElseThrow().getId());
        assertEquals(1, brands.findActive(1, 0).size());
        first.setName("Updated");
        assertEquals(1, brands.update(first));
        assertEquals("Updated", brands.findById(first.getId()).orElseThrow().getName());
        assertEquals(1, brands.deleteById(first.getId()));
        assertTrue(brands.findById(first.getId()).isEmpty());
        assertTrue(brands.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void duplicateSlugAndBlankNameAreRejected() {
        brands.insert(brand("same"));
        BrandEntity duplicate = brand("SAME");
        assertThrows(DataIntegrityViolationException.class, () -> brands.insert(duplicate));
        BrandEntity blank = brand("blank");
        blank.setName(" ");
        assertThrows(DataIntegrityViolationException.class, () -> brands.insert(blank));
    }

    @Test
    void jsonObjectAndNullOptionalFieldsRoundTrip() {
        BrandEntity value = brand("json");
        value.setDescription(null);
        value.setMetadata(
                java.util.Map.of(
                        "array", java.util.List.of(1, 2), "nested", java.util.Map.of("ok", true)));
        brands.insert(value);
        assertEquals(
                value.getMetadata(), brands.findById(value.getId()).orElseThrow().getMetadata());
    }

    @Test
    void deletingBrandReferencedByProductIsRestricted() {
        BrandEntity value = brand("referenced");
        brands.insert(value);
        var product = product("brand-reference");
        product.setBrandId(value.getId());
        products.insert(product);
        assertThrows(DataIntegrityViolationException.class, () -> brands.deleteById(value.getId()));
        assertTrue(brands.findById(value.getId()).isPresent());
    }

    @Test
    void batchInsertSupportsEmptyAndMultipleInputs() {
        assertEquals(0, brands.insertBatch(java.util.List.of()).length);
        assertEquals(0, brands.insertBatch(null).length);
        assertEquals(2, brands.insertBatch(java.util.List.of(brand("a"), brand("b"))).length);
    }

    @Test
    void paginationRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> brands.findActive(0, 0));
        assertThrows(IllegalArgumentException.class, () -> brands.findActive(10, -1));
    }
}
