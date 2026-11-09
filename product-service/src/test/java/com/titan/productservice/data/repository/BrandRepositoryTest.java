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
import com.titan.productservice.data.entity.BrandEntity;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.exception.DuplicateBrandSlugException;
import com.titan.productservice.data.exception.ProductCheckConstraintViolationException;
import com.titan.productservice.data.exception.ProductDataException;
import com.titan.productservice.data.exception.ProductForeignKeyViolationException;
import com.titan.productservice.data.exception.ProductSQLExceptionTranslator;
import com.titan.productservice.data.exception.ProductStringTruncationException;
import com.titan.productservice.presentation.dto.BrandListQuery;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, BrandRepository.class, ProductRepository.class, ProductDataSourceConfig.class, ProductSQLExceptionTranslator.class})
class BrandRepositoryTest {

    @Autowired
    private BrandRepository brands;
    @Autowired
    private ProductRepository products;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM product_categories");
        jdbcTemplate.update("DELETE FROM product_media");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM brands");
    }

    @Test
    void insertFindByIdAndFindBySlugCaseInsensitively() {
        BrandEntity first = brand("first");
        brands.insert(first);

        Assertions.assertThat(brands.findById(first.getId())).get().extracting(BrandEntity::getId).isEqualTo(first.getId());

        Assertions.assertThat(brands.findBySlug("FIRST")).get().extracting(BrandEntity::getId).isEqualTo(first.getId());

        Assertions.assertThat(brands.findBySlug("fIrSt")).get().extracting(BrandEntity::getId).isEqualTo(first.getId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Assertions.assertThat(brands.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findBySlugReturnsEmptyForUnknownSlug() {
        Assertions.assertThat(brands.findBySlug("missing")).isEmpty();
    }

    @Test
    void updateMutatesAllMutableFields() {
        BrandEntity value = brand("original");
        brands.insert(value);

        value.setName("Updated Name");
        value.setSlug("updated-slug");
        value.setDescription("desc");
        value.setWebsiteUrl("https://example.com");
        value.setLogoUrl("https://example.com/logo.png");
        value.setMetadata(Map.of("k", "v"));
        value.setActive(false);

        Assertions.assertThat(brands.update(value)).isEqualTo(1);

        BrandEntity reloaded = brands.findById(value.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getName()).isEqualTo("Updated Name");
        Assertions.assertThat(reloaded.getSlug()).isEqualTo("updated-slug");
        Assertions.assertThat(reloaded.getDescription()).isEqualTo("desc");
        Assertions.assertThat(reloaded.getWebsiteUrl()).isEqualTo("https://example.com");
        Assertions.assertThat(reloaded.getLogoUrl()).isEqualTo("https://example.com/logo.png");
        Assertions.assertThat(reloaded.getMetadata()).isEqualTo(Map.of("k", "v"));
        Assertions.assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void updateReturnsZeroForUnknownId() {
        BrandEntity ghost = brand("ghost");
        Assertions.assertThat(brands.update(ghost)).isZero();
    }

    @Test
    void deleteByIdRemovesRowAndReturnsAffectedCount() {
        BrandEntity value = brand("to-delete");
        brands.insert(value);

        Assertions.assertThat(brands.deleteById(value.getId())).isEqualTo(1);
        Assertions.assertThat(brands.findById(value.getId())).isEmpty();
        Assertions.assertThat(brands.deleteById(value.getId())).isZero();
    }

    @Test
    void findActiveHonoursPaginationAndOrdering() {
        brands.insert(brand("a"));
        brands.insert(brand("b"));
        brands.insert(brand("c"));

        List<BrandEntity> page = brands.findActive(2, 0);
        Assertions.assertThat(page).hasSize(2);
        Assertions.assertThat(page.get(0).getId().toString()).isLessThan(page.get(1).getId().toString());

        Assertions.assertThat(brands.findActive(2, 2)).hasSize(1);
        Assertions.assertThat(brands.findActive(2, 10)).isEmpty();
    }

    @Test
    void findActiveExcludesInactiveBrands() {
        BrandEntity active = brand("active");
        BrandEntity inactive = brand("inactive");
        inactive.setActive(false);
        brands.insert(active);
        brands.insert(inactive);

        Assertions.assertThat(brands.findActive(10, 0)).extracting(BrandEntity::getId).containsExactly(active.getId());
    }

    @Test
    void findPageFiltersAndCounts() {
        BrandEntity alpha = brand("alpha");
        BrandEntity beta = brand("beta");
        BrandEntity inactiveGamma = brand("gamma");
        inactiveGamma.setActive(false);
        brands.insert(alpha);
        brands.insert(beta);
        brands.insert(inactiveGamma);

        BrandListQuery query = new BrandListQuery();
        query.setPage(0);
        query.setSize(10);
        query.setActive(true);

        BrandRepository.BrandPage page = brands.findPage(query);
        Assertions.assertThat(page.totalElements()).isEqualTo(2);
        Assertions.assertThat(page.brands()).hasSize(2);

        query.setName("ALPH");
        page = brands.findPage(query);
        Assertions.assertThat(page.totalElements()).isEqualTo(1);
        Assertions.assertThat(page.brands().getFirst().getId()).isEqualTo(alpha.getId());

        query.setName(null);
        query.setSlug("BET");
        page = brands.findPage(query);
        Assertions.assertThat(page.totalElements()).isEqualTo(1);
        Assertions.assertThat(page.brands().getFirst().getId()).isEqualTo(beta.getId());
    }

    @Test
    void insertBatchHandlesNullAndEmpty() {
        Assertions.assertThat(brands.insertBatch(null)).isEmpty();
        Assertions.assertThat(brands.insertBatch(List.of())).isEmpty();
    }

    @Test
    void insertBatchPersistsAllRows() {
        BrandEntity a = brand("batch-a");
        BrandEntity b = brand("batch-b");
        Assertions.assertThat(brands.insertBatch(List.of(a, b))).hasSize(2);
        Assertions.assertThat(brands.findById(a.getId())).isPresent();
        Assertions.assertThat(brands.findById(b.getId())).isPresent();
    }

    @Test
    void jsonRoundTripPreservesNestedStructuresAndNullDescription() {
        BrandEntity value = brand("json");
        value.setDescription(null);
        value.setMetadata(Map.of("array", List.of(1, 2, 3), "nested", Map.of("ok", true, "n", 1.5)));
        brands.insert(value);

        BrandEntity reloaded = brands.findById(value.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getDescription()).isNull();
        Assertions.assertThat(reloaded.getMetadata()).isEqualTo(value.getMetadata());
    }

    @Test
    void nullMetadataIsStoredAsEmptyObject() {
        BrandEntity value = brand("null-meta");
        value.setMetadata(null);
        brands.insert(value);

        Assertions.assertThat(brands.findById(value.getId()).orElseThrow().getMetadata()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPaginationBounds")
    void findActiveRejectsInvalidBounds(int limit, int offset) {
        Assertions.assertThatThrownBy(() -> brands.findActive(limit, offset)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidPaginationBounds() {
        return Stream.of(Arguments.of(0, 0), Arguments.of(-1, 0), Arguments.of(10, -1), Arguments.of(10, -100));
    }

    @ParameterizedTest(name = "page={0} size={1}")
    @MethodSource("invalidPageBounds")
    void findPageRejectsInvalidBounds(int page, int size) {
        BrandListQuery query = new BrandListQuery();
        query.setPage(page);
        query.setSize(size);

        Assertions.assertThatThrownBy(() -> brands.findPage(query)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidPageBounds() {
        return Stream.of(Arguments.of(-1, 10), Arguments.of(0, 0), Arguments.of(0, 101), Arguments.of(0, -5));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicateSlugIsRejectedCaseInsensitively() {
        brands.insert(brand("same"));

        Assertions.assertThatThrownBy(() -> brands.insert(brand("SAME"))).isInstanceOf(DuplicateBrandSlugException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void blankNameIsRejected() {
        BrandEntity blank = brand("blank");
        blank.setName(" ");

        Assertions.assertThatThrownBy(() -> brands.insert(blank)).isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicateIdInBatchFailsWholeStatement() {
        BrandEntity a = brand("batch-dup-a");
        brands.insert(a);

        BrandEntity duplicate = brand("batch-dup-b");
        duplicate.setId(a.getId());

        Assertions.assertThatThrownBy(() -> brands.insertBatch(List.of(a, duplicate))).isInstanceOf(ProductDataException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void deletingBrandReferencedByProductIsRejected() {
        BrandEntity value = brand("referenced");
        brands.insert(value);

        ProductEntity product = product("brand-reference");
        product.setBrandId(value.getId());
        products.insert(product);

        Assertions.assertThatThrownBy(() -> brands.deleteById(value.getId())).isInstanceOf(ProductForeignKeyViolationException.class);

        Assertions.assertThat(brands.findById(value.getId())).isPresent();
    }

    @Test
    void updateAdvancesUpdatedAtViaTrigger() {
        BrandEntity value = brand("trigger-test");
        brands.insert(value);
        OffsetDateTime originalUpdatedAt = brands.findById(value.getId()).orElseThrow().getUpdatedAt();

        value.setName("Triggered");
        brands.update(value);

        OffsetDateTime newUpdatedAt = brands.findById(value.getId()).orElseThrow().getUpdatedAt();
        Assertions.assertThat(newUpdatedAt).isAfter(originalUpdatedAt);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void batchFailureDoesNotCommitAnyRow() {
        BrandEntity a = brand("batch-a");
        BrandEntity duplicate = brand("batch-a"); // same slug
        Assertions.assertThatThrownBy(() -> brands.insertBatch(List.of(a, duplicate))).isInstanceOf(ProductDataException.class);
        Assertions.assertThat(brands.findById(a.getId())).isEmpty();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void updateToExistingSlugIsRejected() {
        brands.insert(brand("taken"));
        BrandEntity other = brand("other");
        brands.insert(other);
        other.setSlug("TAKEN");
        Assertions.assertThatThrownBy(() -> brands.update(other)).isInstanceOf(DuplicateBrandSlugException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void updateToBlankNameIsRejected() {
        BrandEntity value = brand("blank-update");
        brands.insert(value);
        value.setName(" ");
        Assertions.assertThatThrownBy(() -> brands.update(value)).isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    @Test
    void findPageRespectsPageOffset() {
        for (int i = 0; i < 5; i++) brands.insert(brand("p-" + i));
        BrandListQuery query = new BrandListQuery();
        query.setPage(1);
        query.setSize(2);
        BrandRepository.BrandPage page = brands.findPage(query);
        Assertions.assertThat(page.totalElements()).isEqualTo(5);
        Assertions.assertThat(page.brands()).hasSize(2);
    }

    @Test
    void metadataWithNullValueRoundTrips() {
        Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("k", null);
        BrandEntity value = brand("meta-null");
        value.setMetadata(meta);
        brands.insert(value);
        Assertions.assertThat(brands.findById(value.getId()).orElseThrow().getMetadata()).containsEntry("k", null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nameLongerThan150CharsIsRejected() {
        BrandEntity longName = brand("long-name");
        longName.setName("x".repeat(151));
        Assertions.assertThatThrownBy(() -> brands.insert(longName)).isInstanceOf(ProductStringTruncationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void slugLongerThan180CharsIsRejected() {
        BrandEntity longSlug = brand("x".repeat(181));
        Assertions.assertThatThrownBy(() -> brands.insert(longSlug)).isInstanceOf(ProductStringTruncationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void metadataMustBeJsonObject() {
        Assertions.assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO brands(id, name, slug, metadata) VALUES (?, ?, ?, ?::jsonb)", UUID.randomUUID(), "B", "b", "[1,2,3]")).isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    private BrandEntity brand(String slug) {
        return new BrandEntity(UUID.randomUUID(), "Brand " + slug, slug, null, null, null, Map.of("nested", Map.of("enabled", true)), true, null, null);
    }

    private ProductEntity product(String slug) {
        return new ProductEntity(UUID.randomUUID(), null, "Product " + slug, slug, null, "DRAFT", Map.of("nested", Map.of("value", 1)), 0, null, null, null, null);
    }
}
