package com.titan.productservice.data.repository;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.config.ProductDataSourceConfig;
import com.titan.productservice.data.entity.*;
import com.titan.productservice.data.exception.*;
import com.titan.productservice.presentation.dto.PublicProductListQuery;
import java.time.OffsetDateTime;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    TestcontainersConfiguration.class,
    BrandRepository.class,
    CategoryRepository.class,
    ProductRepository.class,
    ProductCategoryRepository.class,
    ProductVariantRepository.class,
    ProductDataSourceConfig.class,
    ProductSQLExceptionTranslator.class
})
class ProductRepositoryTest {

    @Autowired private ProductRepository products;
    @Autowired private BrandRepository brands;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductCategoryRepository productCategories;
    @Autowired private ProductVariantRepository variants;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM product_categories");
        jdbcTemplate.update("DELETE FROM product_media");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("UPDATE categories SET parent_id = NULL");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM brands");
    }

    @Test
    void insertPersistsProductAndPopulatesTimestamps() {
        ProductEntity value = product("insert");
        products.insert(value);

        ProductEntity reloaded = products.findById(value.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getId()).isEqualTo(value.getId());
        Assertions.assertThat(reloaded.getName()).isEqualTo(value.getName());
        Assertions.assertThat(reloaded.getSlug()).isEqualTo(value.getSlug());
        Assertions.assertThat(reloaded.getStatus()).isEqualTo("DRAFT");
        Assertions.assertThat(reloaded.getVersion()).isZero();
        Assertions.assertThat(reloaded.getMetadata()).isEqualTo(value.getMetadata());
        Assertions.assertThat(reloaded.getCreatedAt()).isNotNull();
        Assertions.assertThat(reloaded.getUpdatedAt()).isNotNull();
        Assertions.assertThat(reloaded.getDeletedAt()).isNull();
        Assertions.assertThat(reloaded.getPublishedAt()).isNull();
    }

    @Test
    void findByIdAndFindBySlugCaseInsensitively() {
        ProductEntity value = product("case");
        products.insert(value);

        Assertions.assertThat(products.findById(value.getId()))
                .get()
                .extracting(ProductEntity::getId)
                .isEqualTo(value.getId());

        Assertions.assertThat(products.findBySlug("CASE"))
                .get()
                .extracting(ProductEntity::getId)
                .isEqualTo(value.getId());

        Assertions.assertThat(products.findBySlug("cAsE"))
                .get()
                .extracting(ProductEntity::getId)
                .isEqualTo(value.getId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Assertions.assertThat(products.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findBySlugReturnsEmptyForUnknownSlug() {
        Assertions.assertThat(products.findBySlug("missing")).isEmpty();
    }

    @Test
    void findActiveByIdExcludesNonActiveProducts() {
        ProductEntity draft = product("draft");
        products.insert(draft);
        Assertions.assertThat(products.findActiveById(draft.getId())).isEmpty();

        ProductEntity active = product("active");
        active.setStatus("ACTIVE");
        active.setPublishedAt(OffsetDateTime.now());
        products.insert(active);
        Assertions.assertThat(products.findActiveById(active.getId())).isPresent();
    }

    @Test
    void findBySlugExcludesDeletedProducts() {
        ProductEntity value = product("deleted-slug");
        products.insert(value);
        products.softDeleteById(value.getId(), 0);

        Assertions.assertThat(products.findBySlug("deleted-slug")).isEmpty();
        Assertions.assertThat(products.findIncludingDeletedById(value.getId())).isPresent();
    }

    @Test
    void updateMutatesAllMutableFieldsAndIncrementsVersion() {
        ProductEntity value = product("update");
        products.insert(value);

        value.setName("Updated");
        value.setSlug("updated-slug");
        value.setDescription("desc");
        value.setStatus("ACTIVE");
        value.setPublishedAt(OffsetDateTime.now());
        value.setMetadata(Map.of("k", "v"));

        Assertions.assertThat(products.update(value)).isEqualTo(1);

        ProductEntity reloaded = products.findById(value.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getName()).isEqualTo("Updated");
        Assertions.assertThat(reloaded.getSlug()).isEqualTo("updated-slug");
        Assertions.assertThat(reloaded.getDescription()).isEqualTo("desc");
        Assertions.assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
        Assertions.assertThat(reloaded.getMetadata()).isEqualTo(Map.of("k", "v"));
        Assertions.assertThat(reloaded.getVersion()).isEqualTo(1);
        Assertions.assertThat(reloaded.getPublishedAt()).isNotNull();
    }

    @Test
    void updateWithStaleVersionIsRejected() {
        ProductEntity value = product("stale-update");
        products.insert(value);

        products.incrementVersion(value.getId(), 0);

        value.setName("Updated");
        Assertions.assertThatThrownBy(() -> products.update(value))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void updateOnDeletedProductIsRejected() {
        ProductEntity value = product("update-deleted");
        products.insert(value);
        products.softDeleteById(value.getId(), 0);

        value.setName("Updated");
        Assertions.assertThatThrownBy(() -> products.update(value))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void softDeleteByIdMarksDeletedAndBumpsVersion() {
        ProductEntity value = product("soft-delete");
        products.insert(value);

        var result = products.softDeleteById(value.getId(), 0).orElseThrow();

        Assertions.assertThat(result.id()).isEqualTo(value.getId());
        Assertions.assertThat(result.version()).isEqualTo(1);
        Assertions.assertThat(result.deletedAt()).isNotNull();

        ProductEntity reloaded = products.findById(value.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getDeletedAt()).isNotNull();
        Assertions.assertThat(reloaded.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    void softDeleteByIdWithStaleVersionReturnsEmpty() {
        ProductEntity value = product("soft-delete-stale");
        products.insert(value);

        Assertions.assertThat(products.softDeleteById(value.getId(), 99)).isEmpty();
    }

    @Test
    void softDeleteByIdTwiceReturnsEmptyOnSecondCall() {
        ProductEntity value = product("soft-delete-twice");
        products.insert(value);
        products.softDeleteById(value.getId(), 0);

        Assertions.assertThat(products.softDeleteById(value.getId(), 1)).isEmpty();
    }

    @Test
    void incrementVersionBumpsVersionAndReturnsNewValue() {
        ProductEntity value = product("increment");
        products.insert(value);

        long version = products.incrementVersion(value.getId(), 0);

        Assertions.assertThat(version).isEqualTo(1);
        Assertions.assertThat(products.findById(value.getId()).orElseThrow().getVersion())
                .isEqualTo(1);
    }

    @Test
    void incrementVersionWithStaleVersionIsRejected() {
        ProductEntity value = product("increment-stale");
        products.insert(value);

        Assertions.assertThatThrownBy(() -> products.incrementVersion(value.getId(), 99))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void incrementVersionOnDeletedProductIsRejected() {
        ProductEntity value = product("increment-deleted");
        products.insert(value);
        products.softDeleteById(value.getId(), 0);

        Assertions.assertThatThrownBy(() -> products.incrementVersion(value.getId(), 1))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void incrementVersionOnUnknownProductIsRejected() {
        Assertions.assertThatThrownBy(() -> products.incrementVersion(UUID.randomUUID(), 0))
                .isInstanceOf(ProductConcurrentModificationException.class);
    }

    @Test
    void findActiveOrdersByCreatedAtDescAndExcludesNonActiveAndDeleted() {
        ProductEntity first = activeProduct("active-a");
        ProductEntity second = activeProduct("active-b");
        ProductEntity draft = product("draft-c");
        products.insert(first);
        products.insert(second);
        products.insert(draft);

        OffsetDateTime base = OffsetDateTime.now().minusDays(1);
        jdbcTemplate.update("UPDATE products SET created_at = ? WHERE id = ?", base, first.getId());
        jdbcTemplate.update(
                "UPDATE products SET created_at = ? WHERE id = ?",
                base.plusSeconds(1),
                second.getId());

        Assertions.assertThat(products.findActive(10, 0))
                .extracting(ProductEntity::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findActiveHonoursPagination() {
        products.insert(activeProduct("a"));
        products.insert(activeProduct("b"));
        products.insert(activeProduct("c"));

        Assertions.assertThat(products.findActive(2, 0)).hasSize(2);
        Assertions.assertThat(products.findActive(2, 2)).hasSize(1);
        Assertions.assertThat(products.findActive(2, 10)).isEmpty();
    }

    @Test
    void insertBatchHandlesNullEmptyAndMultiple() {
        Assertions.assertThat(products.insertBatch(null)).isEmpty();
        Assertions.assertThat(products.insertBatch(List.of())).isEmpty();

        ProductEntity a = product("batch-a");
        ProductEntity b = product("batch-b");
        Assertions.assertThat(products.insertBatch(List.of(a, b))).hasSize(2);
        Assertions.assertThat(products.findById(a.getId())).isPresent();
        Assertions.assertThat(products.findById(b.getId())).isPresent();
    }

    @Test
    void findPageFiltersByStatusBrandCategoryDeletedAndText() {
        BrandEntity brand = brand("brand");
        brands.insert(brand);
        CategoryEntity category = category("category");
        categories.insert(category);

        ProductEntity active = activeProduct("active");
        active.setBrandId(brand.getId());
        ProductEntity draft = product("draft");
        ProductEntity deleted = product("deleted");
        products.insert(active);
        products.insert(draft);
        products.insert(deleted);
        products.softDeleteById(deleted.getId(), 0);
        productCategories.insert(
                new ProductCategoryEntity(active.getId(), category.getId(), false, null), 0);

        ProductRepository.ProductFilter activeOnly =
                new ProductRepository.ProductFilter(
                        0, 10, "ACTIVE", null, null, null, null, null, null, null, null, null,
                        null);
        Assertions.assertThat(products.findPage(activeOnly).totalElements()).isEqualTo(1);

        ProductRepository.ProductFilter byBrand =
                new ProductRepository.ProductFilter(
                        0,
                        10,
                        null,
                        brand.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Assertions.assertThat(products.findPage(byBrand).totalElements()).isEqualTo(1);
        Assertions.assertThat(products.findPage(byBrand).products().getFirst().getId())
                .isEqualTo(active.getId());

        ProductRepository.ProductFilter byCategory =
                new ProductRepository.ProductFilter(
                        0,
                        10,
                        null,
                        null,
                        category.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        Assertions.assertThat(products.findPage(byCategory).totalElements()).isEqualTo(1);
        Assertions.assertThat(products.findPage(byCategory).products().getFirst().getId())
                .isEqualTo(active.getId());

        ProductRepository.ProductFilter onlyDeleted =
                new ProductRepository.ProductFilter(
                        0, 10, null, null, null, true, null, null, null, null, null, null, null);
        Assertions.assertThat(products.findPage(onlyDeleted).totalElements()).isEqualTo(1);
        Assertions.assertThat(products.findPage(onlyDeleted).products().getFirst().getId())
                .isEqualTo(deleted.getId());

        ProductRepository.ProductFilter byName =
                new ProductRepository.ProductFilter(
                        0, 10, null, null, null, null, null, null, null, null, null, "ACTIVE",
                        null);
        Assertions.assertThat(products.findPage(byName).totalElements()).isEqualTo(1);

        ProductRepository.ProductFilter bySlug =
                new ProductRepository.ProductFilter(
                        0, 10, null, null, null, null, null, null, null, null, null, null, "draft");
        Assertions.assertThat(products.findPage(bySlug).totalElements()).isEqualTo(1);
    }

    @Test
    void findPageRespectsPageOffset() {
        for (int i = 0; i < 5; i++) {
            products.insert(product("p-" + i));
        }

        ProductRepository.ProductFilter filter =
                new ProductRepository.ProductFilter(
                        1, 2, null, null, null, null, null, null, null, null, null, null, null);

        ProductRepository.ProductPage page = products.findPage(filter);
        Assertions.assertThat(page.totalElements()).isEqualTo(5);
        Assertions.assertThat(page.products()).hasSize(2);
    }

    @Test
    void findPageSortsByCreatedAtDescendingByDefault() {
        ProductEntity first = product("sort-a");
        products.insert(first);
        ProductEntity second = product("sort-b");
        products.insert(second);

        OffsetDateTime base = OffsetDateTime.now().minusDays(1);
        jdbcTemplate.update("UPDATE products SET created_at = ? WHERE id = ?", base, first.getId());
        jdbcTemplate.update(
                "UPDATE products SET created_at = ? WHERE id = ?",
                base.plusSeconds(1),
                second.getId());

        ProductRepository.ProductFilter filter =
                new ProductRepository.ProductFilter(
                        0, 10, null, null, null, null, null, null, null, null, null, null, null);

        Assertions.assertThat(products.findPage(filter).products())
                .extracting(ProductEntity::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findPublicPageReturnsOnlyActiveNonDeleted() {
        ProductEntity active = activeProduct("pub-active");
        ProductEntity draft = product("pub-draft");
        ProductEntity deleted = activeProduct("pub-deleted");
        products.insert(active);
        products.insert(draft);
        products.insert(deleted);
        products.softDeleteById(deleted.getId(), 0);

        PublicProductListQuery query = new PublicProductListQuery();
        query.setPage(0);
        query.setSize(10);

        Assertions.assertThat(products.findPublicPage(query).totalElements()).isEqualTo(1);
        Assertions.assertThat(products.findPublicPage(query).products().getFirst().getId())
                .isEqualTo(active.getId());
    }

    @Test
    void findPublicPageFiltersByTextBrandAndCategory() {
        BrandEntity brand = brand("public-brand");
        brands.insert(brand);
        CategoryEntity category = category("public-category");
        categories.insert(category);

        ProductEntity matching = activeProduct("matching");
        matching.setBrandId(brand.getId());
        ProductEntity other = activeProduct("other");
        products.insert(matching);
        products.insert(other);
        productCategories.insert(
                new ProductCategoryEntity(matching.getId(), category.getId(), false, null), 0);

        PublicProductListQuery textQuery = new PublicProductListQuery();
        textQuery.setPage(0);
        textQuery.setSize(10);
        textQuery.setQ("match");
        Assertions.assertThat(products.findPublicPage(textQuery).totalElements()).isEqualTo(1);

        PublicProductListQuery brandQuery = new PublicProductListQuery();
        brandQuery.setPage(0);
        brandQuery.setSize(10);
        brandQuery.setBrandId(brand.getId());
        Assertions.assertThat(products.findPublicPage(brandQuery).totalElements()).isEqualTo(1);

        PublicProductListQuery categoryQuery = new PublicProductListQuery();
        categoryQuery.setPage(0);
        categoryQuery.setSize(10);
        categoryQuery.setCategoryId(category.getId());
        Assertions.assertThat(products.findPublicPage(categoryQuery).totalElements()).isEqualTo(1);
    }

    @Test
    void findPublicPageFeaturedFiltersByMetadata() {
        ProductEntity featured = activeProduct("featured");
        featured.setMetadata(Map.of("featured", true));
        products.insert(featured);
        products.insert(activeProduct("not-featured"));

        PublicProductListQuery query = new PublicProductListQuery();
        query.setPage(0);
        query.setSize(10);

        Assertions.assertThat(
                        products.findPublicPage(query, ProductRepository.DiscoveryMode.FEATURED)
                                .totalElements())
                .isEqualTo(1);
    }

    @Test
    void findPublicPageNewArrivalsOrdersByPublishedAt() {
        ProductEntity first = activeProduct("new-arrival-a");
        first.setPublishedAt(OffsetDateTime.now().minusDays(1));
        ProductEntity second = activeProduct("new-arrival-b");
        second.setPublishedAt(OffsetDateTime.now());
        products.insert(first);
        products.insert(second);

        PublicProductListQuery query = new PublicProductListQuery();
        query.setPage(0);
        query.setSize(10);

        Assertions.assertThat(
                        products.findPublicPage(query, ProductRepository.DiscoveryMode.NEW_ARRIVALS)
                                .products())
                .extracting(ProductEntity::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void findPublicPageFiltersByPriceRange() {
        ProductEntity cheap = activeProduct("cheap");
        ProductEntity expensive = activeProduct("expensive");
        products.insert(cheap);
        products.insert(expensive);
        variants.insert(variant(cheap.getId(), "SKU-CHEAP", 10));
        variants.insert(variant(expensive.getId(), "SKU-EXPENSIVE", 100));

        PublicProductListQuery query = new PublicProductListQuery();
        query.setPage(0);
        query.setSize(10);
        query.setMinPrice(java.math.BigDecimal.valueOf(50));

        Assertions.assertThat(products.findPublicPage(query).totalElements()).isEqualTo(1);
        Assertions.assertThat(products.findPublicPage(query).products().getFirst().getId())
                .isEqualTo(expensive.getId());
    }

    @Test
    void findPublicPageRejectsInvalidBounds() {
        Assertions.assertThatThrownBy(
                        () -> {
                            PublicProductListQuery query = new PublicProductListQuery();
                            query.setPage(-1);
                            query.setSize(10);
                            products.findPublicPage(query);
                        })
                .isInstanceOf(IllegalArgumentException.class);

        Assertions.assertThatThrownBy(
                        () -> {
                            PublicProductListQuery query = new PublicProductListQuery();
                            query.setPage(0);
                            query.setSize(0);
                            products.findPublicPage(query);
                        })
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findActiveRejectsInvalidBounds() {
        Assertions.assertThatThrownBy(() -> products.findActive(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> products.findActive(10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "page={0} size={1}")
    @MethodSource("invalidPageBounds")
    void findPageRejectsInvalidBounds(int page, int size) {
        Assertions.assertThatThrownBy(
                        () ->
                                new ProductRepository.ProductFilter(
                                        page, size, null, null, null, null, null, null, null, null,
                                        null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidPageBounds() {
        return Stream.of(Arguments.of(-1, 10), Arguments.of(0, 0), Arguments.of(0, 101));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicateSlugIsRejectedCaseInsensitively() {
        products.insert(product("same"));

        Assertions.assertThatThrownBy(() -> products.insert(product("SAME")))
                .isInstanceOf(DuplicateProductSlugException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicateSlugAfterSoftDeleteIsAllowed() {
        ProductEntity value = product("reuse-slug");
        products.insert(value);
        products.softDeleteById(value.getId(), 0);

        ProductEntity replacement = product("reuse-slug");
        Assertions.assertThatCode(() -> products.insert(replacement)).doesNotThrowAnyException();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicatePrimaryMediaBlockedByUniqueIndex() {
        ProductEntity value = product("primary-media-dupe");
        products.insert(value);
        jdbcTemplate.update(
                "INSERT INTO product_media(id, product_id, media_type, storage_key, position, is_primary) "
                        + "VALUES (?, ?, 'IMAGE', 'k1', 0, TRUE)",
                UUID.randomUUID(),
                value.getId());

        Assertions.assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO product_media(id, product_id, media_type, storage_key, position, is_primary) "
                                                + "VALUES (?, ?, 'IMAGE', 'k2', 1, TRUE)",
                                        UUID.randomUUID(),
                                        value.getId()))
                .isInstanceOf(DuplicatePrimaryMediaException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void blankNameIsRejected() {
        ProductEntity value = product("blank-name");
        value.setName(" ");

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nullNameIsRejected() {
        ProductEntity value = product("null-name");
        value.setName(null);

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(ProductNotNullViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nullSlugIsRejected() {
        ProductEntity value = product("null-slug");
        value.setSlug(null);

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(ProductNotNullViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nameLongerThan255CharsIsRejected() {
        ProductEntity value = product("long-name");
        value.setName("x".repeat(256));

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(ProductStringTruncationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void slugLongerThan280CharsIsRejected() {
        ProductEntity value = product("x".repeat(281));

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(ProductStringTruncationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void invalidStatusIsRejected() {
        ProductEntity value = product("invalid-status");
        value.setStatus("UNKNOWN");

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void activeStatusWithoutPublishedAtIsRejected() {
        ProductEntity value = product("active-no-publish");
        value.setStatus("ACTIVE");

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(InvalidProductStatusTransitionException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void insertWithUnknownBrandIsRejected() {
        ProductEntity value = product("unknown-brand");
        value.setBrandId(UUID.randomUUID());

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(ProductForeignKeyViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void negativeVersionIsRejected() {
        ProductEntity value = product("negative-version");
        value.setVersion(-1);

        Assertions.assertThatThrownBy(() -> products.insert(value))
                .isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void batchFailureDoesNotCommitAnyRow() {
        ProductEntity first = product("batch-fail-a");
        ProductEntity duplicate = product("batch-fail-a");

        Assertions.assertThatThrownBy(() -> products.insertBatch(List.of(first, duplicate)))
                .isInstanceOf(ProductUniqueViolationException.class);

        Assertions.assertThat(products.findById(first.getId())).isEmpty();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void activeProductWithoutActiveVariantIsRejectedAtCommit() {
        Assertions.assertThatThrownBy(
                        () -> {
                            ProductEntity value = activeProduct("no-variant");
                            products.insert(value);
                            jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");
                        })
                .isInstanceOf(ActiveProductRequiresVariantException.class);
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

    private ProductEntity activeProduct(String slug) {
        ProductEntity value = product(slug);
        value.setStatus("ACTIVE");
        value.setPublishedAt(OffsetDateTime.now());
        return value;
    }

    private BrandEntity brand(String slug) {
        return new BrandEntity(
                UUID.randomUUID(),
                "Brand " + slug,
                slug,
                null,
                null,
                null,
                Map.of(),
                true,
                null,
                null);
    }

    private CategoryEntity category(String slug) {
        return new CategoryEntity(
                UUID.randomUUID(),
                null,
                "Category " + slug,
                slug,
                null,
                Map.of(),
                0,
                true,
                null,
                null);
    }

    private ProductVariantEntity variant(UUID productId, String sku, long price) {
        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setId(UUID.randomUUID());
        variant.setProductId(productId);
        variant.setSku(sku);
        variant.setPrice(java.math.BigDecimal.valueOf(price));
        variant.setStatus("ACTIVE");
        return variant;
    }
}
