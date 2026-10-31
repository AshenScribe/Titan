package com.titan.productservice.data.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ProductMediaRepositoryIntegrationTests extends AbstractRepositoryIntegrationTest {
    @Test
    void insertFindUpdateAndDeleteMaintainsOrdering() {
        var product = product("media");
        products.insert(product);
        var first = media(product.getId(), "IMAGE", 0);
        media.insert(first, 0);
        var second = media(product.getId(), "VIDEO", 1);
        media.insert(second, 1);
        assertEquals(first.getId(), media.findByProductId(product.getId()).getFirst().getId());
        first.setAltText("updated");
        assertEquals(1, media.update(first, 2));
        assertEquals(1, media.deleteById(second.getId(), product.getId(), 3));
        assertTrue(media.findById(second.getId()).isEmpty());
    }

    @Test
    void mediaTypePositionAndPrimaryConstraintsAreEnforced() {
        var product = product("media-constraints");
        products.insert(product);
        var first = media(product.getId(), "IMAGE", 0);
        first.setPrimary(true);
        media.insert(first, 0);
        var second = media(product.getId(), "IMAGE", 0);
        assertThrows(DataIntegrityViolationException.class, () -> media.insert(second, 1));
        var invalid = media(product.getId(), "AUDIO", 1);
        assertThrows(DataIntegrityViolationException.class, () -> media.insert(invalid, 1));
    }

    @Test
    void positionSwapAndPrimaryMediaSwitchWorkWithinOneTransaction() {
        var product = product("media-switch");
        products.insert(product);
        var first = media(product.getId(), "IMAGE", 0);
        first.setPrimary(true);
        var second = media(product.getId(), "VIDEO", 1);
        media.insert(first, 0);
        media.insert(second, 1);
        jdbc.update(
                "UPDATE product_media SET position = CASE WHEN position = 0 THEN 2 ELSE 0 END WHERE product_id = ?",
                product.getId());
        first.setPosition(2);
        first.setPrimary(false);
        second.setPosition(0);
        second.setPrimary(true);
        assertEquals(1, media.update(first, 2));
        assertEquals(1, media.update(second, 3));
        assertEquals(second.getId(), media.findByProductId(product.getId()).getFirst().getId());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_media WHERE product_id = ? AND is_primary",
                        Integer.class,
                        product.getId()));
    }

    @Test
    void batchAndMissingOperationsHaveExplicitResults() {
        var product = product("media-batch");
        products.insert(product);
        assertEquals(0, media.insertBatch(List.of()).length);
        assertEquals(0, media.insertBatch(null).length);
        assertEquals(
                2,
                media.insertBatch(
                                List.of(
                                        media(product.getId(), "IMAGE", 0),
                                        media(product.getId(), "VIDEO", 1)))
                        .length);
        assertTrue(media.findById(java.util.UUID.randomUUID()).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> media.deleteById(java.util.UUID.randomUUID()));
    }
}
