// ------------------------------------------------------------------------------
// MIT License
//
// Copyright (c) 2026 Rishabh Saraswat
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
// ------------------------------------------------------------------------------

package com.titan.productservice.data.repository;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.config.ProductDataSourceConfig;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.entity.ProductMediaEntity;
import com.titan.productservice.data.exception.DuplicatePrimaryMediaException;
import com.titan.productservice.data.exception.InvalidMediaPositionException;
import com.titan.productservice.data.exception.ProductCheckConstraintViolationException;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import com.titan.productservice.data.exception.ProductNotNullViolationException;
import com.titan.productservice.data.exception.ProductSQLExceptionTranslator;
import com.titan.productservice.data.exception.ProductStringTruncationException;
import com.titan.productservice.data.exception.ProductUniqueViolationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfiguration.class,
    ProductRepository.class,
    ProductMediaRepository.class,
    ProductDataSourceConfig.class,
    ProductSQLExceptionTranslator.class
})
class ProductMediaRepositoryTest {

    @Autowired private ProductMediaRepository mediaRepository;
    @Autowired private ProductRepository products;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM product_categories");
        jdbcTemplate.update("DELETE FROM product_media");
        jdbcTemplate.update("DELETE FROM products");
    }

    @Test
    void insertPersistsMediaAndPopulatesCreatedAt() {
        ProductEntity product = product("insert");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);

        mediaRepository.insert(media, 0);

        ProductMediaEntity reloaded = mediaRepository.findById(media.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getProductId()).isEqualTo(product.getId());
        Assertions.assertThat(reloaded.getMediaType()).isEqualTo("IMAGE");
        Assertions.assertThat(reloaded.getStorageKey()).isEqualTo("s3://bucket/key.png");
        Assertions.assertThat(reloaded.getAltText()).isEqualTo("alt");
        Assertions.assertThat(reloaded.getPosition()).isZero();
        Assertions.assertThat(reloaded.isPrimary()).isFalse();
        Assertions.assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void findByProductIdOrdersByPositionThenId() {
        ProductEntity product = product("ordered");
        products.insert(product);
        ProductMediaEntity third = media(product.getId(), 2, false);
        ProductMediaEntity first = media(product.getId(), 0, false);
        ProductMediaEntity second = media(product.getId(), 1, false);

        mediaRepository.insert(third, 0);
        mediaRepository.insert(first, 1);
        mediaRepository.insert(second, 2);

        Assertions.assertThat(mediaRepository.findByProductId(product.getId()))
                .extracting(ProductMediaEntity::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Assertions.assertThat(mediaRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByProductIdReturnsEmptyForUnknownProduct() {
        Assertions.assertThat(mediaRepository.findByProductId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void updateMutatesAllMutableFields() {
        ProductEntity product = product("update");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        mediaRepository.insert(media, 0);

        media.setMediaType("VIDEO");
        media.setStorageKey("s3://bucket/key.mp4");
        media.setAltText("video alt");
        media.setPosition(5);
        media.setPrimary(true);

        Assertions.assertThat(mediaRepository.update(media, 1)).isEqualTo(1);

        ProductMediaEntity reloaded = mediaRepository.findById(media.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getMediaType()).isEqualTo("VIDEO");
        Assertions.assertThat(reloaded.getStorageKey()).isEqualTo("s3://bucket/key.mp4");
        Assertions.assertThat(reloaded.getAltText()).isEqualTo("video alt");
        Assertions.assertThat(reloaded.getPosition()).isEqualTo(5);
        Assertions.assertThat(reloaded.isPrimary()).isTrue();
    }

    @Test
    void updateOnMissingMediaIsRejected() {
        ProductEntity product = product("update-missing");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);

        Assertions.assertThatThrownBy(() -> mediaRepository.update(media, 0))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void deleteByIdRemovesRowAndReturnsAffectedCount() {
        ProductEntity product = product("delete");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        mediaRepository.insert(media, 0);

        Assertions.assertThat(mediaRepository.deleteById(media.getId(), product.getId(), 1))
                .isEqualTo(1);
        Assertions.assertThat(mediaRepository.findById(media.getId())).isEmpty();

        Assertions.assertThatThrownBy(
                        () -> mediaRepository.deleteById(media.getId(), product.getId(), 2))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void deleteByIdWithoutProductIdOnUnknownMediaIsRejected() {
        Assertions.assertThatThrownBy(() -> mediaRepository.deleteById(UUID.randomUUID()))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void deleteByIdWithoutProductIdResolvesProductAndVersion() {
        ProductEntity product = product("delete-resolve");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        mediaRepository.insert(media, 0);

        Assertions.assertThat(mediaRepository.deleteById(media.getId())).isEqualTo(1);
        Assertions.assertThat(mediaRepository.findById(media.getId())).isEmpty();
    }

    @Test
    void insertReplacingPrimaryReplacesExistingPrimary() {
        ProductEntity product = product("replace-primary");
        products.insert(product);
        ProductMediaEntity first = media(product.getId(), 0, true);
        ProductMediaEntity second = media(product.getId(), 1, true);

        mediaRepository.insertReplacingPrimary(first, 0);
        mediaRepository.insertReplacingPrimary(second, 1);

        List<ProductMediaEntity> all = mediaRepository.findByProductId(product.getId());
        Assertions.assertThat(all).hasSize(2);
        Assertions.assertThat(all.stream().filter(ProductMediaEntity::isPrimary).count())
                .isEqualTo(1);
        Assertions.assertThat(
                        all.stream()
                                .filter(ProductMediaEntity::isPrimary)
                                .findFirst()
                                .orElseThrow()
                                .getId())
                .isEqualTo(second.getId());
    }

    @Test
    void updateReplacingPrimaryClearsPreviousPrimary() {
        ProductEntity product = product("update-replace");
        products.insert(product);
        ProductMediaEntity first = media(product.getId(), 0, true);
        ProductMediaEntity second = media(product.getId(), 1, false);
        mediaRepository.insert(first, 0);
        mediaRepository.insert(second, 1);

        second.setPrimary(true);
        Assertions.assertThat(mediaRepository.updateReplacingPrimary(second, 2)).isEqualTo(1);

        List<ProductMediaEntity> all = mediaRepository.findByProductId(product.getId());
        Assertions.assertThat(all.stream().filter(ProductMediaEntity::isPrimary).count())
                .isEqualTo(1);
        Assertions.assertThat(
                        all.stream()
                                .filter(ProductMediaEntity::isPrimary)
                                .findFirst()
                                .orElseThrow()
                                .getId())
                .isEqualTo(second.getId());
    }

    @Test
    void setPrimaryClearsPreviousPrimaryAndSetsNew() {
        ProductEntity product = product("set-primary");
        products.insert(product);
        ProductMediaEntity first = media(product.getId(), 0, true);
        ProductMediaEntity second = media(product.getId(), 1, false);
        mediaRepository.insert(first, 0);
        mediaRepository.insert(second, 1);

        Assertions.assertThat(mediaRepository.setPrimary(product.getId(), second.getId(), 2))
                .isEqualTo(1);

        List<ProductMediaEntity> all = mediaRepository.findByProductId(product.getId());
        Assertions.assertThat(all.stream().filter(ProductMediaEntity::isPrimary).count())
                .isEqualTo(1);
        Assertions.assertThat(
                        all.stream()
                                .filter(ProductMediaEntity::isPrimary)
                                .findFirst()
                                .orElseThrow()
                                .getId())
                .isEqualTo(second.getId());
    }

    @Test
    void setPrimaryOnForeignMediaIsRejected() {
        ProductEntity owner = product("set-primary-owner");
        ProductEntity other = product("set-primary-other");
        products.insert(owner);
        products.insert(other);
        ProductMediaEntity media = media(owner.getId(), 0, false);
        mediaRepository.insert(media, 0);

        Assertions.assertThatThrownBy(
                        () -> mediaRepository.setPrimary(other.getId(), media.getId(), 0))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void reorderUpdatesPositionsInOrder() {
        ProductEntity product = product("reorder");
        products.insert(product);
        ProductMediaEntity a = media(product.getId(), 0, false);
        ProductMediaEntity b = media(product.getId(), 1, false);
        ProductMediaEntity c = media(product.getId(), 2, false);
        mediaRepository.insert(a, 0);
        mediaRepository.insert(b, 1);
        mediaRepository.insert(c, 2);

        mediaRepository.reorder(product.getId(), List.of(c.getId(), a.getId(), b.getId()), 3);

        Assertions.assertThat(mediaRepository.findByProductId(product.getId()))
                .extracting(ProductMediaEntity::getId)
                .containsExactly(c.getId(), a.getId(), b.getId());
    }

    @Test
    void reorderWithNullListIsNoOp() {
        ProductEntity product = product("reorder-null");
        products.insert(product);

        mediaRepository.reorder(product.getId(), null, 0);

        Assertions.assertThat(mediaRepository.findByProductId(product.getId())).isEmpty();
    }

    @Test
    void insertBatchHandlesNullEmptyAndMultiple() {
        ProductEntity product = product("batch");
        products.insert(product);
        ProductMediaEntity a = media(product.getId(), 0, false);
        ProductMediaEntity b = media(product.getId(), 1, false);

        Assertions.assertThat(mediaRepository.insertBatch(null)).isEmpty();
        Assertions.assertThat(mediaRepository.insertBatch(List.of())).isEmpty();
        Assertions.assertThat(mediaRepository.insertBatch(List.of(a, b))).hasSize(2);

        Assertions.assertThat(mediaRepository.findByProductId(product.getId())).hasSize(2);
    }

    @Test
    void insertBatchRejectsMultipleDistinctProducts() {
        ProductEntity p1 = product("batch-p1");
        ProductEntity p2 = product("batch-p2");
        products.insert(p1);
        products.insert(p2);

        Assertions.assertThatThrownBy(
                        () ->
                                mediaRepository.insertBatch(
                                        List.of(
                                                media(p1.getId(), 0, false),
                                                media(p2.getId(), 0, false))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void insertWithUnknownProductIsRejected() {
        ProductMediaEntity media = media(UUID.randomUUID(), 0, false);

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(media, 0))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void insertWithoutVersionOnUnknownProductIsRejected() {
        ProductMediaEntity media = media(UUID.randomUUID(), 0, false);

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(media))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void staleProductVersionIsRejectedOnInsert() {
        ProductEntity product = product("stale-insert");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(media, 99))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void staleProductVersionIsRejectedOnUpdate() {
        ProductEntity product = product("stale-update");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        mediaRepository.insert(media, 0);

        Assertions.assertThatThrownBy(() -> mediaRepository.update(media, 99))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void staleProductVersionIsRejectedOnDelete() {
        ProductEntity product = product("stale-delete");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        mediaRepository.insert(media, 0);

        Assertions.assertThatThrownBy(
                        () -> mediaRepository.deleteById(media.getId(), product.getId(), 99))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void staleProductVersionIsRejectedOnReorder() {
        ProductEntity product = product("stale-reorder");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        mediaRepository.insert(media, 0);

        Assertions.assertThatThrownBy(
                        () -> mediaRepository.reorder(product.getId(), List.of(media.getId()), 99))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void staleProductVersionIsRejectedOnSetPrimary() {
        ProductEntity product = product("stale-set-primary");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        mediaRepository.insert(media, 0);

        Assertions.assertThatThrownBy(
                        () -> mediaRepository.setPrimary(product.getId(), media.getId(), 99))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @ParameterizedTest(name = "staleVersion={0}")
    @MethodSource("staleVersions")
    void everyMutationRejectsStaleVersion(long staleVersion) {
        ProductEntity product = product("stale-" + staleVersion);
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        mediaRepository.insert(media, 0);

        Assertions.assertThatThrownBy(() -> mediaRepository.update(media, staleVersion))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    static Stream<Arguments> staleVersions() {
        return Stream.of(Arguments.of(0L), Arguments.of(5L), Arguments.of(99L));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicatePrimaryMediaIsRejected() {
        ProductEntity product = product("dup-primary");
        products.insert(product);
        ProductMediaEntity first = media(product.getId(), 0, true);
        ProductMediaEntity second = media(product.getId(), 1, true);
        mediaRepository.insert(first, 0);

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(second, 1))
                .isInstanceOf(DuplicatePrimaryMediaException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicatePositionIsRejectedAtCommit() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        Assertions.assertThatThrownBy(
                        () ->
                                template.executeWithoutResult(
                                        status -> {
                                            ProductEntity product = product("dup-position");
                                            products.insert(product);

                                            ProductMediaEntity first =
                                                    media(product.getId(), 0, false);
                                            ProductMediaEntity second =
                                                    media(product.getId(), 0, false);

                                            mediaRepository.insert(first, 0);
                                            mediaRepository.insert(second, 1);
                                        }))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void negativePositionIsRejected() {
        ProductEntity product = product("negative-position");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), -1, false);

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(media, 0))
                .isInstanceOf(InvalidMediaPositionException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void invalidMediaTypeIsRejected() {
        ProductEntity product = product("bad-type");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        media.setMediaType("AUDIO");

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(media, 0))
                .isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nullStorageKeyIsRejected() {
        ProductEntity product = product("null-storage");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        media.setStorageKey(null);

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(media, 0))
                .isInstanceOf(ProductNotNullViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nullMediaTypeIsRejected() {
        ProductEntity product = product("null-type");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        media.setMediaType(null);

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(media, 0))
                .isInstanceOf(ProductNotNullViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void altTextLongerThan500CharsIsRejected() {
        ProductEntity product = product("long-alt");
        products.insert(product);
        ProductMediaEntity media = media(product.getId(), 0, false);
        media.setAltText("x".repeat(501));

        Assertions.assertThatThrownBy(() -> mediaRepository.insert(media, 0))
                .isInstanceOf(ProductStringTruncationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void batchFailureDoesNotCommitAnyRow() {
        ProductEntity product = product("batch-fail");
        products.insert(product);
        ProductMediaEntity first = media(product.getId(), 0, false);
        ProductMediaEntity duplicate = media(product.getId(), 1, false);
        duplicate.setId(first.getId());

        Assertions.assertThatThrownBy(() -> mediaRepository.insertBatch(List.of(first, duplicate)))
                .isInstanceOf(ProductUniqueViolationException.class);

        Assertions.assertThat(mediaRepository.findByProductId(product.getId())).isEmpty();
    }

    private ProductMediaEntity media(UUID productId, int position, boolean primary) {
        ProductMediaEntity media = new ProductMediaEntity();
        media.setId(UUID.randomUUID());
        media.setProductId(productId);
        media.setMediaType("IMAGE");
        media.setStorageKey("s3://bucket/key.png");
        media.setAltText("alt");
        media.setPosition(position);
        media.setPrimary(primary);
        return media;
    }

    private ProductEntity product(String slug) {
        return new ProductEntity(
                UUID.randomUUID(),
                null,
                "Product " + slug,
                slug,
                null,
                "DRAFT",
                Map.of("nested", Map.of("value", 1)),
                0,
                null,
                null,
                null,
                null);
    }
}
