package com.titan.productservice.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

class CategoryRepositoryIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Test
    void rootChildrenHierarchyLookupUpdateAndDelete() {
        var root = category("root", null);
        categories.insert(root);
        var child = category("child", root.getId());
        categories.insert(child);
        var grandchild = category("grandchild", child.getId());
        categories.insert(grandchild);
        assertEquals(
                child.getId(), categories.findChildren(root.getId(), 10, 0).getFirst().getId());
        assertEquals(root.getId(), categories.findBySlug("ROOT").orElseThrow().getId());
        child.setDescription("updated");
        assertEquals(1, categories.update(child));
        assertEquals(1, categories.deleteById(grandchild.getId()));
    }

    @Test
    void duplicateSlugInvalidParentSelfParentAndCycleAreRejected() {
        var root = category("same", null);
        categories.insert(root);
        assertThrows(
                DataIntegrityViolationException.class,
                () -> categories.insert(category("SAME", null)));
        var invalid = category("invalid-parent", java.util.UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class, () -> categories.insert(invalid));
        var self = category("self", null);
        self.setParentId(self.getId());
        assertThrows(DataAccessException.class, () -> categories.insert(self));
        var child = category("child", root.getId());
        categories.insert(child);
        root.setParentId(child.getId());
        assertThrows(DataAccessException.class, () -> categories.update(root));
    }

    @Test
    void deletingReferencedCategoryOrParentWithChildrenIsRestricted() {
        var parent = category("parent", null);
        categories.insert(parent);
        var child = category("child", parent.getId());
        categories.insert(child);
        assertThrows(
                DataIntegrityViolationException.class, () -> categories.deleteById(parent.getId()));
        var product = product("category-reference");
        products.insert(product);
        productCategories.insert(
                new com.titan.productservice.data.entity.ProductCategoryEntity(
                        product.getId(), child.getId(), false, null),
                0);
        assertThrows(
                DataIntegrityViolationException.class, () -> categories.deleteById(child.getId()));
    }

    @Test
    void metadataAndPaginationAreSupported() {
        categories.insert(category("a", null));
        categories.insert(category("b", null));
        assertEquals(1, categories.findChildren(null, 1, 0).size());
    }

    @Test
    void paginationRejectsInvalidBoundsAndBatchAcceptsNull() {
        assertThrows(IllegalArgumentException.class, () -> categories.findActive(0, 0));
        assertThrows(IllegalArgumentException.class, () -> categories.findChildren(null, 10, -1));
        assertEquals(0, categories.insertBatch(null).length);
    }

    @Test
    void movingCategoryUpdatesParentAndTimestamp() {
        var firstRoot = category("timestamp-root-a", null);
        var secondRoot = category("timestamp-root-b", null);
        var child = category("timestamp-child", firstRoot.getId());
        categories.insert(firstRoot);
        categories.insert(secondRoot);
        categories.insert(child);
        var before = categories.findById(child.getId()).orElseThrow();

        child.setParentId(secondRoot.getId());
        assertEquals(1, categories.update(child));

        var after = categories.findById(child.getId()).orElseThrow();
        assertEquals(secondRoot.getId(), after.getParentId());
        assertNotNull(after.getUpdatedAt());
        assertFalse(after.getUpdatedAt().isBefore(before.getUpdatedAt()));
    }
}
