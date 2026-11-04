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

import com.titan.productservice.entity.ProductCategoryEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ProductCategoryRepositoryIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Test
    void insertFindUpdateAndDeleteRelationship() {
        var product = product("categories");
        products.insert(product);
        var first = category("first", null);
        var second = category("second", null);
        categories.insert(first);
        categories.insert(second);
        var relation = new ProductCategoryEntity(product.getId(), first.getId(), true, null);
        productCategories.insert(relation, 0);
        assertEquals(1, productCategories.findByProductId(product.getId()).size());
        assertEquals(1, productCategories.findByCategoryId(first.getId()).size());
        relation.setPrimary(false);
        assertEquals(1, productCategories.update(relation, 1));
        assertEquals(1, productCategories.delete(product.getId(), first.getId(), 2));
    }

    @Test
    void duplicateAndMultiplePrimaryRelationshipsAreRejected() {
        var product = product("category-unique");
        products.insert(product);
        var first = category("first", null);
        var second = category("second", null);
        categories.insert(first);
        categories.insert(second);
        productCategories.insert(
                new ProductCategoryEntity(product.getId(), first.getId(), true, null), 0);
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        productCategories.insert(
                                new ProductCategoryEntity(
                                        product.getId(), first.getId(), false, null),
                                1));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        productCategories.insert(
                                new ProductCategoryEntity(
                                        product.getId(), second.getId(), true, null),
                                1));
    }

    @Test
    void multipleCategoriesAndPrimaryCategorySwitchAreSupported() {
        var product = product("category-switch");
        products.insert(product);
        var first = category("switch-first", null);
        var second = category("switch-second", null);
        categories.insert(first);
        categories.insert(second);
        var firstRelation = new ProductCategoryEntity(product.getId(), first.getId(), true, null);
        var secondRelation =
                new ProductCategoryEntity(product.getId(), second.getId(), false, null);
        productCategories.insert(firstRelation, 0);
        productCategories.insert(secondRelation, 1);
        firstRelation.setPrimary(false);
        secondRelation.setPrimary(true);
        assertEquals(1, productCategories.update(firstRelation, 2));
        assertEquals(1, productCategories.update(secondRelation, 3));
        assertEquals(2, productCategories.findByProductId(product.getId()).size());
        assertEquals(
                second.getId(),
                productCategories.findByProductId(product.getId()).getFirst().getCategoryId());
    }

    @Test
    void batchEmptyAndMultipleAreSupported() {
        var product = product("category-batch");
        products.insert(product);
        var category = category("batch-category", null);
        categories.insert(category);
        assertEquals(0, productCategories.insertBatch(List.of()).length);
        assertEquals(
                1,
                productCategories.insertBatch(
                                List.of(
                                        new ProductCategoryEntity(
                                                product.getId(), category.getId(), false, null)))
                        .length);
        assertEquals(0, productCategories.insertBatch(null).length);
    }
}
