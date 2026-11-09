package com.titan.productservice.data.repository;

import com.titan.productservice.TestcontainersConfiguration;
import com.titan.productservice.config.ProductDataSourceConfig;
import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.data.entity.ProductCategoryEntity;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.data.exception.CategoryHierarchyCycleException;
import com.titan.productservice.data.exception.DuplicateCategorySlugException;
import com.titan.productservice.data.exception.ProductCheckConstraintViolationException;
import com.titan.productservice.data.exception.ProductForeignKeyViolationException;
import com.titan.productservice.data.exception.ProductNotNullViolationException;
import com.titan.productservice.data.exception.ProductSQLExceptionTranslator;
import com.titan.productservice.data.exception.ProductStringTruncationException;
import com.titan.productservice.data.exception.ProductUniqueViolationException;
import com.titan.productservice.presentation.dto.CategoryListQuery;
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
    CategoryRepository.class,
    ProductRepository.class,
    ProductCategoryRepository.class,
    ProductDataSourceConfig.class,
    ProductSQLExceptionTranslator.class
})
class CategoryRepositoryTest {

    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductCategoryRepository productCategories;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM product_categories");
        jdbcTemplate.update("DELETE FROM product_media");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("UPDATE categories SET parent_id = NULL");
        jdbcTemplate.update("DELETE FROM categories");
    }

    @Test
    void insertFindByIdAndFindBySlugCaseInsensitively() {
        CategoryEntity root = category("root", null);
        categories.insert(root);

        Assertions.assertThat(categories.findById(root.getId()))
                .get()
                .extracting(CategoryEntity::getId)
                .isEqualTo(root.getId());

        Assertions.assertThat(categories.findBySlug("ROOT"))
                .get()
                .extracting(CategoryEntity::getId)
                .isEqualTo(root.getId());

        Assertions.assertThat(categories.findBySlug("rOoT"))
                .get()
                .extracting(CategoryEntity::getId)
                .isEqualTo(root.getId());
    }

    @Test
    void insertPopulatesTimestamps() {
        CategoryEntity value = category("stamped", null);
        categories.insert(value);

        CategoryEntity reloaded = categories.findById(value.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getCreatedAt()).isNotNull();
        Assertions.assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Assertions.assertThat(categories.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findBySlugReturnsEmptyForUnknownSlug() {
        Assertions.assertThat(categories.findBySlug("missing")).isEmpty();
    }

    @Test
    void updateMutatesAllMutableFields() {
        CategoryEntity parent = category("parent", null);
        categories.insert(parent);

        CategoryEntity value = category("original", null);
        categories.insert(value);

        value.setParentId(parent.getId());
        value.setName("Updated Name");
        value.setSlug("updated-slug");
        value.setDescription("desc");
        value.setMetadata(Map.of("k", "v"));
        value.setSortOrder(42);
        value.setActive(false);

        Assertions.assertThat(categories.update(value)).isEqualTo(1);

        CategoryEntity reloaded = categories.findById(value.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getParentId()).isEqualTo(parent.getId());
        Assertions.assertThat(reloaded.getName()).isEqualTo("Updated Name");
        Assertions.assertThat(reloaded.getSlug()).isEqualTo("updated-slug");
        Assertions.assertThat(reloaded.getDescription()).isEqualTo("desc");
        Assertions.assertThat(reloaded.getMetadata()).isEqualTo(Map.of("k", "v"));
        Assertions.assertThat(reloaded.getSortOrder()).isEqualTo(42);
        Assertions.assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void updateAdvancesUpdatedAt() {
        CategoryEntity value = category("ts", null);
        categories.insert(value);
        OffsetDateTime before = categories.findById(value.getId()).orElseThrow().getUpdatedAt();

        value.setName("triggered");
        categories.update(value);

        OffsetDateTime after = categories.findById(value.getId()).orElseThrow().getUpdatedAt();
        Assertions.assertThat(after).isAfterOrEqualTo(before);
    }

    @Test
    void updateReturnsZeroForUnknownId() {
        CategoryEntity ghost = category("ghost", null);
        Assertions.assertThat(categories.update(ghost)).isZero();
    }

    @Test
    void deleteByIdRemovesRowAndReturnsAffectedCount() {
        CategoryEntity value = category("to-delete", null);
        categories.insert(value);

        Assertions.assertThat(categories.deleteById(value.getId())).isEqualTo(1);
        Assertions.assertThat(categories.findById(value.getId())).isEmpty();
        Assertions.assertThat(categories.deleteById(value.getId())).isZero();
    }

    @Test
    void findChildrenReturnsOnlyDirectChildrenOrderedBySortOrderThenId() {
        CategoryEntity root = category("root", null);
        categories.insert(root);

        CategoryEntity late = category("late", root.getId());
        late.setSortOrder(20);
        CategoryEntity early = category("early", root.getId());
        early.setSortOrder(10);
        CategoryEntity unrelated = category("unrelated", null);

        categories.insert(late);
        categories.insert(early);
        categories.insert(unrelated);

        List<CategoryEntity> children = categories.findChildren(root.getId(), 10, 0);
        Assertions.assertThat(children)
                .extracting(CategoryEntity::getId)
                .containsExactly(early.getId(), late.getId());
    }

    @Test
    void findChildrenHandlesNullParentForRoots() {
        CategoryEntity a = category("a", null);
        CategoryEntity b = category("b", null);
        categories.insert(a);
        categories.insert(b);

        Assertions.assertThat(categories.findChildren(null, 10, 0))
                .extracting(CategoryEntity::getId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void findChildrenHonoursPagination() {
        CategoryEntity root = category("root", null);
        categories.insert(root);
        for (int i = 0; i < 3; i++) {
            CategoryEntity child = category("c-" + i, root.getId());
            child.setSortOrder(i);
            categories.insert(child);
        }

        Assertions.assertThat(categories.findChildren(root.getId(), 2, 0)).hasSize(2);
        Assertions.assertThat(categories.findChildren(root.getId(), 2, 2)).hasSize(1);
        Assertions.assertThat(categories.findChildren(root.getId(), 2, 10)).isEmpty();
    }

    @Test
    void findActiveExcludesInactiveAndOrdersBySortOrderThenId() {
        CategoryEntity activeLate = category("active-late", null);
        activeLate.setSortOrder(2);
        CategoryEntity activeEarly = category("active-early", null);
        activeEarly.setSortOrder(1);
        CategoryEntity inactive = category("inactive", null);
        inactive.setActive(false);

        categories.insert(activeLate);
        categories.insert(activeEarly);
        categories.insert(inactive);

        Assertions.assertThat(categories.findActive(10, 0))
                .extracting(CategoryEntity::getId)
                .containsExactly(activeEarly.getId(), activeLate.getId());
    }

    @Test
    void findByIdsReturnsMatchingRowsAndEmptyForEmptyInput() {
        CategoryEntity a = category("a", null);
        CategoryEntity b = category("b", null);
        categories.insert(a);
        categories.insert(b);

        Assertions.assertThat(categories.findByIds(List.of(a.getId(), b.getId())))
                .extracting(CategoryEntity::getId)
                .containsExactlyInAnyOrder(a.getId(), b.getId());

        Assertions.assertThat(categories.findByIds(List.of())).isEmpty();
    }

    @Test
    void findPageFiltersAndCounts() {
        CategoryEntity alpha = category("alpha", null);
        CategoryEntity beta = category("beta", null);
        CategoryEntity inactiveGamma = category("gamma", null);
        inactiveGamma.setActive(false);
        categories.insert(alpha);
        categories.insert(beta);
        categories.insert(inactiveGamma);

        CategoryListQuery query = new CategoryListQuery();
        query.setPage(0);
        query.setSize(10);
        query.setActive(true);

        CategoryRepository.CategoryPage page = categories.findPage(query);
        Assertions.assertThat(page.totalElements()).isEqualTo(2);
        Assertions.assertThat(page.categories()).hasSize(2);

        query.setName("ALPH");
        page = categories.findPage(query);
        Assertions.assertThat(page.totalElements()).isEqualTo(1);
        Assertions.assertThat(page.categories().getFirst().getId()).isEqualTo(alpha.getId());

        query.setName(null);
        query.setParentId(alpha.getId());
        page = categories.findPage(query);
        Assertions.assertThat(page.totalElements()).isZero();
        Assertions.assertThat(page.categories()).isEmpty();
    }

    @Test
    void findPageRespectsPageOffset() {
        for (int i = 0; i < 5; i++) {
            categories.insert(category("p-" + i, null));
        }
        CategoryListQuery query = new CategoryListQuery();
        query.setPage(1);
        query.setSize(2);

        CategoryRepository.CategoryPage page = categories.findPage(query);
        Assertions.assertThat(page.totalElements()).isEqualTo(5);
        Assertions.assertThat(page.categories()).hasSize(2);
    }

    @Test
    void findAllHierarchyReturnsEveryRowOrdered() {
        CategoryEntity root = category("root", null);
        CategoryEntity child = category("child", root.getId());
        categories.insert(root);
        categories.insert(child);

        Assertions.assertThat(categories.findAllHierarchy())
                .extracting(CategoryEntity::getId)
                .containsExactly(root.getId(), child.getId());
    }

    @Test
    void findDescendantsWalksWholeSubtree() {
        CategoryEntity root = category("root", null);
        CategoryEntity child = category("child", root.getId());
        CategoryEntity grandchild = category("grandchild", child.getId());
        CategoryEntity unrelated = category("unrelated", null);
        categories.insert(root);
        categories.insert(child);
        categories.insert(grandchild);
        categories.insert(unrelated);

        Assertions.assertThat(categories.findDescendants(root.getId()))
                .extracting(CategoryEntity::getId)
                .containsExactlyInAnyOrder(child.getId(), grandchild.getId());

        Assertions.assertThat(categories.findDescendants(unrelated.getId())).isEmpty();
    }

    @Test
    void insertBatchHandlesNullEmptyAndMultiple() {
        Assertions.assertThat(categories.insertBatch(null)).isEmpty();
        Assertions.assertThat(categories.insertBatch(List.of())).isEmpty();

        CategoryEntity a = category("batch-a", null);
        CategoryEntity b = category("batch-b", null);
        Assertions.assertThat(categories.insertBatch(List.of(a, b))).hasSize(2);
        Assertions.assertThat(categories.findById(a.getId())).isPresent();
        Assertions.assertThat(categories.findById(b.getId())).isPresent();
    }

    @Test
    void jsonRoundTripPreservesNestedStructuresAndNullDescription() {
        CategoryEntity value = category("json", null);
        value.setDescription(null);
        value.setMetadata(Map.of("array", List.of(1, 2, 3), "nested", Map.of("ok", true)));
        categories.insert(value);

        CategoryEntity reloaded = categories.findById(value.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getDescription()).isNull();
        Assertions.assertThat(reloaded.getMetadata()).isEqualTo(value.getMetadata());
    }

    @Test
    void nullMetadataIsStoredAsEmptyObject() {
        CategoryEntity value = category("null-meta", null);
        value.setMetadata(null);
        categories.insert(value);

        Assertions.assertThat(categories.findById(value.getId()).orElseThrow().getMetadata())
                .isEmpty();
    }

    @ParameterizedTest(name = "limit={0} offset={1}")
    @MethodSource("invalidBounds")
    void findActiveRejectsInvalidBounds(int limit, int offset) {
        Assertions.assertThatThrownBy(() -> categories.findActive(limit, offset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "limit={0} offset={1}")
    @MethodSource("invalidBounds")
    void findChildrenRejectsInvalidBounds(int limit, int offset) {
        Assertions.assertThatThrownBy(() -> categories.findChildren(null, limit, offset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidBounds() {
        return Stream.of(
                Arguments.of(0, 0),
                Arguments.of(-1, 0),
                Arguments.of(10, -1),
                Arguments.of(10, -100));
    }

    @ParameterizedTest(name = "page={0} size={1}")
    @MethodSource("invalidPageBounds")
    void findPageRejectsInvalidBounds(int page, int size) {
        CategoryListQuery query = new CategoryListQuery();
        query.setPage(page);
        query.setSize(size);

        Assertions.assertThatThrownBy(() -> categories.findPage(query))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidPageBounds() {
        return Stream.of(
                Arguments.of(-1, 10),
                Arguments.of(0, 0),
                Arguments.of(0, 101),
                Arguments.of(0, -5));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void duplicateSlugIsRejectedCaseInsensitively() {
        categories.insert(category("same", null));

        Assertions.assertThatThrownBy(() -> categories.insert(category("SAME", null)))
                .isInstanceOf(DuplicateCategorySlugException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void updateToExistingSlugIsRejected() {
        categories.insert(category("taken", null));
        CategoryEntity other = category("other", null);
        categories.insert(other);

        other.setSlug("TAKEN");
        Assertions.assertThatThrownBy(() -> categories.update(other))
                .isInstanceOf(DuplicateCategorySlugException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void insertWithInvalidParentIdIsRejected() {
        CategoryEntity invalid = category("invalid-parent", UUID.randomUUID());

        Assertions.assertThatThrownBy(() -> categories.insert(invalid))
                .isInstanceOf(ProductForeignKeyViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void updateCreatingCycleIsRejected() {
        CategoryEntity root = category("root", null);
        categories.insert(root);
        CategoryEntity child = category("child", root.getId());
        categories.insert(child);

        root.setParentId(child.getId());
        Assertions.assertThatThrownBy(() -> categories.update(root))
                .isInstanceOf(CategoryHierarchyCycleException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void deletingParentWithChildrenIsRejected() {
        CategoryEntity parent = category("parent", null);
        categories.insert(parent);
        categories.insert(category("child", parent.getId()));

        Assertions.assertThatThrownBy(() -> categories.deleteById(parent.getId()))
                .isInstanceOf(ProductForeignKeyViolationException.class);

        Assertions.assertThat(categories.findById(parent.getId())).isPresent();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void deletingCategoryReferencedByProductCategoryIsRejected() {
        CategoryEntity value = category("referenced", null);
        categories.insert(value);

        ProductEntity product = product("category-reference");
        products.insert(product);
        productCategories.insert(
                new ProductCategoryEntity(product.getId(), value.getId(), false, null), 0);

        Assertions.assertThatThrownBy(() -> categories.deleteById(value.getId()))
                .isInstanceOf(ProductForeignKeyViolationException.class);

        Assertions.assertThat(categories.findById(value.getId())).isPresent();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void blankNameIsRejected() {
        CategoryEntity blank = category("blank", null);
        blank.setName(" ");

        Assertions.assertThatThrownBy(() -> categories.insert(blank))
                .isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nameLongerThan150CharsIsRejected() {
        CategoryEntity longName = category("long-name", null);
        longName.setName("x".repeat(151));

        Assertions.assertThatThrownBy(() -> categories.insert(longName))
                .isInstanceOf(ProductStringTruncationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void slugLongerThan180CharsIsRejected() {
        CategoryEntity longSlug = category("x".repeat(181), null);

        Assertions.assertThatThrownBy(() -> categories.insert(longSlug))
                .isInstanceOf(ProductStringTruncationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nullNameIsRejected() {
        CategoryEntity value = category("null-name", null);
        value.setName(null);

        Assertions.assertThatThrownBy(() -> categories.insert(value))
                .isInstanceOf(ProductNotNullViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void nullSlugIsRejected() {
        CategoryEntity value = category("null-slug", null);
        value.setSlug(null);

        Assertions.assertThatThrownBy(() -> categories.insert(value))
                .isInstanceOf(ProductNotNullViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void metadataMustBeJsonObject() {
        Assertions.assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO categories(id, name, slug, metadata) VALUES (?, ?, ?, ?::jsonb)",
                                        UUID.randomUUID(),
                                        "B",
                                        "b",
                                        "[1,2,3]"))
                .isInstanceOf(ProductCheckConstraintViolationException.class);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Test
    void batchFailureDoesNotCommitAnyRow() {
        CategoryEntity a = category("batch-a", null);
        CategoryEntity duplicate = category("batch-a", null);

        Assertions.assertThatThrownBy(() -> categories.insertBatch(List.of(a, duplicate)))
                .isInstanceOf(ProductUniqueViolationException.class);

        Assertions.assertThat(categories.findById(a.getId())).isEmpty();
    }

    private CategoryEntity category(String slug, UUID parentId) {
        return new CategoryEntity(
                UUID.randomUUID(),
                parentId,
                "Category " + slug,
                slug,
                null,
                Map.of("nested", Map.of("enabled", true)),
                0,
                true,
                null,
                null);
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
