package com.titan.productservice.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.productservice.cache.ProductChangedEvent;
import com.titan.productservice.data.entity.ProductEntity;
import com.titan.productservice.presentation.dto.PublicProductListQuery;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductRepository {
    private static final String COLUMNS =
            """
      id, brand_id, name, slug, description, status, metadata, version,
      published_at, deleted_at, created_at, updated_at
      """;
    private static final String SELECT_ACTIVE_BY_ID_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM products WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL";
    private static final String SELECT_BY_ID_SQL =
            "SELECT " + COLUMNS + " FROM products WHERE id = ?";
    private static final String SELECT_BY_SLUG_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM products WHERE LOWER(slug) = LOWER(?) AND deleted_at IS NULL";
    private static final String SELECT_ACTIVE_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM products WHERE status = 'ACTIVE' AND deleted_at IS NULL"
                    + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?";
    private static final String INSERT_SQL =
            """
      INSERT INTO products
        (id, brand_id, name, slug, description, status, metadata, version,
         published_at, deleted_at)
      VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
      """;
    private static final String UPDATE_SQL =
            """
      UPDATE products
      SET brand_id = ?, name = ?, slug = ?, description = ?, status = ?,
        metadata = ?::jsonb, version = version + 1, published_at = ?,
        updated_at = NOW()
      WHERE id = ? AND version = ? AND deleted_at IS NULL
      """;
    private static final String SOFT_DELETE_SQL =
            """
      UPDATE products
      SET deleted_at = clock_timestamp(), status = 'INACTIVE', version = version + 1
      WHERE id = ? AND version = ? AND deleted_at IS NULL
      RETURNING id, version, deleted_at
      """;
    private static final String INCREMENT_VERSION_SQL =
            """
      UPDATE products
      SET version = version + 1, updated_at = clock_timestamp()
      WHERE id = ? AND version = ? AND deleted_at IS NULL
      RETURNING version
      """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RowMapper<ProductEntity> rowMapper = this::mapProduct;

    public ProductRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public void insert(ProductEntity product) {
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
                    statement.setObject(1, product.getId());
                    bindInsert(statement, product);
                    return statement;
                });
        eventPublisher.publishEvent(
                new ProductChangedEvent(
                        product.getId(), null, product.getSlug(), product.getVersion(), "CREATED"));
    }

    @Transactional
    public int update(ProductEntity product) {
        String previousSlug =
                jdbcTemplate
                        .query(
                                "SELECT slug FROM products WHERE id = ?",
                                (resultSet, row) -> resultSet.getString(1),
                                product.getId())
                        .stream()
                        .findFirst()
                        .orElse(null);
        int updated =
                jdbcTemplate.update(
                        connection -> {
                            PreparedStatement statement = connection.prepareStatement(UPDATE_SQL);
                            bindUpdate(statement, product);
                            return statement;
                        });
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Product was changed, deleted, or does not exist: " + product.getId());
        }
        eventPublisher.publishEvent(
                new ProductChangedEvent(
                        product.getId(),
                        previousSlug,
                        product.getSlug(),
                        product.getVersion() + 1,
                        "UPDATED"));
        return updated;
    }

    public Optional<ProductEntity> findActiveById(UUID id) {
        return jdbcTemplate.query(SELECT_ACTIVE_BY_ID_SQL, rowMapper, id).stream().findFirst();
    }

    public Optional<ProductEntity> findById(UUID id) {
        return jdbcTemplate.query(SELECT_BY_ID_SQL, rowMapper, id).stream().findFirst();
    }

    public Optional<ProductEntity> findIncludingDeletedById(UUID id) {
        return findById(id);
    }

    public Optional<ProductEntity> findBySlug(String slug) {
        return jdbcTemplate.query(SELECT_BY_SLUG_SQL, rowMapper, slug).stream().findFirst();
    }

    public List<ProductEntity> findActive(int limit, int offset) {
        validatePage(limit, offset);
        return jdbcTemplate.query(SELECT_ACTIVE_SQL, rowMapper, limit, offset);
    }

    public ProductPage findPage(ProductFilter filter) {
        StringBuilder where = new StringBuilder(" FROM products WHERE 1 = 1");
        List<Object> parameters = new java.util.ArrayList<>();
        appendIdentityFilters(where, parameters, filter);
        appendDateFilters(where, parameters, filter);
        appendTextFilters(where, parameters, filter);
        String orderBy = sortSql(filter.sort());
        Integer total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)" + where, Integer.class, parameters.toArray());
        List<Object> pageParameters = new java.util.ArrayList<>(parameters);
        pageParameters.add(filter.size());
        pageParameters.add(filter.page() * filter.size());
        List<ProductEntity> products =
                jdbcTemplate.query(
                        "SELECT " + COLUMNS + where + orderBy + " LIMIT ? OFFSET ?",
                        rowMapper,
                        pageParameters.toArray());
        return new ProductPage(products, total == null ? 0 : total);
    }

    public ProductPage findPublicPage(PublicProductListQuery query) {
        return findPublicPage(query, DiscoveryMode.SEARCH);
    }

    public ProductPage findPublicPage(PublicProductListQuery query, DiscoveryMode discoveryMode) {
        validatePublicPage(query.getPage(), query.getSize());
        StringBuilder where =
                new StringBuilder(
                        " FROM products p WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL");
        List<Object> parameters = new java.util.ArrayList<>();
        appendPublicFilters(where, parameters, query, discoveryMode);
        String orderBy = publicSortSql(query.getSort(), discoveryMode);
        Integer total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)" + where, Integer.class, parameters.toArray());
        List<Object> pageParameters = new java.util.ArrayList<>(parameters);
        pageParameters.add(query.getSize());
        pageParameters.add(query.getPage() * query.getSize());
        List<ProductEntity> products =
                jdbcTemplate.query(
                        "SELECT p."
                                + COLUMNS.replace(", ", ", p.")
                                + where
                                + orderBy
                                + " LIMIT ? OFFSET ?",
                        rowMapper,
                        pageParameters.toArray());
        return new ProductPage(products, total == null ? 0 : total);
    }

    private void appendPublicFilters(
            StringBuilder where,
            List<Object> parameters,
            PublicProductListQuery query,
            DiscoveryMode discoveryMode) {
        appendPublicIdentityFilters(where, parameters, query);
        if (discoveryMode == DiscoveryMode.FEATURED) {
            where.append(" AND p.metadata @> '{\"featured\":true}'::jsonb");
        }
        if (query.getMinPrice() != null
                || query.getMaxPrice() != null
                || query.getCurrency() != null) {
            where.append(
                    " AND EXISTS (SELECT 1 FROM product_variants v"
                            + " WHERE v.product_id = p.id AND v.status = 'ACTIVE'");
            if (query.getMinPrice() != null) {
                where.append(" AND v.price >= ?");
                parameters.add(query.getMinPrice());
            }
            if (query.getMaxPrice() != null) {
                where.append(" AND v.price <= ?");
                parameters.add(query.getMaxPrice());
            }
            appendCurrencyClause(where, parameters, query.getCurrency());
            where.append(")");
        }
    }

    private void appendPublicIdentityFilters(
            StringBuilder where, List<Object> parameters, PublicProductListQuery query) {
        if (query.getQ() != null && !query.getQ().isBlank()) {
            where.append(" AND (p.name ILIKE ? OR p.slug ILIKE ?)");
            String search = "%" + query.getQ().trim() + "%";
            parameters.add(search);
            parameters.add(search);
        }
        if (query.getBrandId() != null) {
            where.append(" AND p.brand_id = ?");
            parameters.add(query.getBrandId());
        }
        if (query.getCategoryId() != null) {
            where.append(
                    " AND EXISTS (SELECT 1 FROM product_categories pc"
                            + " WHERE pc.product_id = p.id AND pc.category_id = ?)");
            parameters.add(query.getCategoryId());
        }
    }

    private void appendCurrencyClause(
            StringBuilder where, List<Object> parameters, String currency) {
        if (currency != null && !currency.isBlank()) {
            where.append(" AND v.currency = ?");
            parameters.add(currency);
        }
    }

    private String publicSortSql(String sort, DiscoveryMode discoveryMode) {
        if (discoveryMode == DiscoveryMode.NEW_ARRIVALS) {
            return " ORDER BY p.published_at DESC NULLS LAST, p.created_at DESC, p.id DESC";
        }
        return switch (sort == null ? "createdAtDesc" : sort) {
            case "createdAtAsc" -> " ORDER BY p.created_at ASC, p.id ASC";
            case "nameAsc" -> " ORDER BY LOWER(p.name) ASC, p.id ASC";
            case "nameDesc" -> " ORDER BY LOWER(p.name) DESC, p.id DESC";
            default -> " ORDER BY p.created_at DESC, p.id DESC";
        };
    }

    public enum DiscoveryMode {
        SEARCH,
        FEATURED,
        NEW_ARRIVALS
    }

    private void validatePublicPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be 1..100");
        }
    }

    private void appendIdentityFilters(
            StringBuilder where, List<Object> parameters, ProductFilter filter) {
        if (filter.status() != null) {
            where.append(" AND status = ?");
            parameters.add(filter.status());
        }
        if (filter.brandId() != null) {
            where.append(" AND brand_id = ?");
            parameters.add(filter.brandId());
        }
        if (filter.categoryId() != null) {
            where.append(
                    " AND EXISTS (SELECT 1 FROM product_categories pc"
                            + " WHERE pc.product_id = products.id AND pc.category_id = ?)");
            parameters.add(filter.categoryId());
        }
        if (filter.deleted() != null) {
            where.append(
                    filter.deleted() ? " AND deleted_at IS NOT NULL" : " AND deleted_at IS NULL");
        }
    }

    private void appendDateFilters(
            StringBuilder where, List<Object> parameters, ProductFilter filter) {
        appendDateFilter(where, parameters, filter.createdFrom(), "created_at >= ?");
        appendDateFilter(where, parameters, filter.createdTo(), "created_at <= ?");
        appendDateFilter(where, parameters, filter.updatedFrom(), "updated_at >= ?");
        appendDateFilter(where, parameters, filter.updatedTo(), "updated_at <= ?");
    }

    private void appendDateFilter(
            StringBuilder where, List<Object> parameters, OffsetDateTime value, String clause) {
        if (value != null) {
            where.append(" AND ").append(clause);
            parameters.add(value);
        }
    }

    private void appendTextFilters(
            StringBuilder where, List<Object> parameters, ProductFilter filter) {
        if (filter.name() != null && !filter.name().isBlank()) {
            where.append(" AND name ILIKE ?");
            parameters.add("%" + filter.name().trim() + "%");
        }
        if (filter.slug() != null && !filter.slug().isBlank()) {
            where.append(" AND slug ILIKE ?");
            parameters.add("%" + filter.slug().trim() + "%");
        }
    }

    private String sortSql(String sort) {
        return switch (sort == null ? "createdAtDesc" : sort) {
            case "createdAtAsc" -> " ORDER BY created_at ASC, id ASC";
            case "updatedAtAsc" -> " ORDER BY updated_at ASC, id ASC";
            case "updatedAtDesc" -> " ORDER BY updated_at DESC, id DESC";
            case "nameAsc" -> " ORDER BY LOWER(name) ASC, id ASC";
            case "nameDesc" -> " ORDER BY LOWER(name) DESC, id DESC";
            default -> " ORDER BY created_at DESC, id DESC";
        };
    }

    public record ProductFilter(
            int page,
            int size,
            String status,
            UUID brandId,
            UUID categoryId,
            Boolean deleted,
            String sort,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            OffsetDateTime updatedFrom,
            OffsetDateTime updatedTo,
            String name,
            String slug) {
        public ProductFilter {
            if (page < 0 || size < 1 || size > 100) {
                throw new IllegalArgumentException(
                        "page must be non-negative and size must be 1..100");
            }
        }
    }

    public record ProductPage(List<ProductEntity> products, int totalElements) {}

    @Transactional
    public Optional<SoftDeletedProduct> softDeleteById(UUID id, long expectedVersion) {
        String previousSlug =
                jdbcTemplate
                        .query(
                                "SELECT slug FROM products WHERE id = ?",
                                (resultSet, row) -> resultSet.getString(1),
                                id)
                        .stream()
                        .findFirst()
                        .orElse(null);
        Optional<SoftDeletedProduct> result =
                jdbcTemplate
                        .query(
                                SOFT_DELETE_SQL,
                                (rs, row) ->
                                        new SoftDeletedProduct(
                                                rs.getObject("id", UUID.class),
                                                rs.getLong("version"),
                                                rs.getObject("deleted_at", OffsetDateTime.class)),
                                id,
                                expectedVersion)
                        .stream()
                        .findFirst();
        result.ifPresent(
                deleted ->
                        eventPublisher.publishEvent(
                                new ProductChangedEvent(
                                        id,
                                        previousSlug,
                                        previousSlug,
                                        deleted.version(),
                                        "DELETED")));
        return result;
    }

    @Transactional
    public long incrementVersion(UUID productId, long expectedVersion) {
        String previousSlug =
                jdbcTemplate
                        .query(
                                "SELECT slug FROM products WHERE id = ?",
                                (resultSet, row) -> resultSet.getString(1),
                                productId)
                        .stream()
                        .findFirst()
                        .orElse(null);
        List<Long> versions =
                jdbcTemplate.query(
                        INCREMENT_VERSION_SQL,
                        (rs, row) -> rs.getLong("version"),
                        productId,
                        expectedVersion);
        if (versions.isEmpty()) {
            throw new OptimisticLockingFailureException(
                    "Product aggregate was changed, deleted, or does not exist: " + productId);
        }
        long version = versions.getFirst();
        eventPublisher.publishEvent(
                new ProductChangedEvent(
                        productId, previousSlug, previousSlug, version, "AGGREGATE_CHANGED"));
        return version;
    }

    public record SoftDeletedProduct(UUID id, long version, OffsetDateTime deletedAt) {}

    public int[] insertBatch(List<ProductEntity> products) {
        if (products == null || products.isEmpty()) {
            return new int[0];
        }
        return jdbcTemplate.batchUpdate(
                INSERT_SQL,
                new BatchPreparedStatementSetter() {
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        ProductEntity product = products.get(index);
                        statement.setObject(1, product.getId());
                        bindInsert(statement, product);
                    }

                    public int getBatchSize() {
                        return products.size();
                    }
                });
    }

    private void bindInsert(PreparedStatement statement, ProductEntity product)
            throws SQLException {
        statement.setObject(2, product.getBrandId());
        statement.setString(3, product.getName());
        statement.setString(4, product.getSlug());
        statement.setString(5, product.getDescription());
        statement.setString(6, product.getStatus());
        statement.setObject(7, json(product.getMetadata()), Types.OTHER);
        statement.setLong(8, product.getVersion());
        statement.setObject(9, product.getPublishedAt());
        statement.setObject(10, product.getDeletedAt());
    }

    private void bindUpdate(PreparedStatement statement, ProductEntity product)
            throws SQLException {
        statement.setObject(1, product.getBrandId());
        statement.setString(2, product.getName());
        statement.setString(3, product.getSlug());
        statement.setString(4, product.getDescription());
        statement.setString(5, product.getStatus());
        statement.setObject(6, json(product.getMetadata()), Types.OTHER);
        statement.setObject(7, product.getPublishedAt());
        statement.setObject(8, product.getId());
        statement.setLong(9, product.getVersion());
    }

    private ProductEntity mapProduct(ResultSet rs, int row) throws SQLException {
        ProductEntity product = new ProductEntity();
        product.setId(rs.getObject("id", UUID.class));
        product.setBrandId(rs.getObject("brand_id", UUID.class));
        product.setName(rs.getString("name"));
        product.setSlug(rs.getString("slug"));
        product.setDescription(rs.getString("description"));
        product.setStatus(rs.getString("status"));
        product.setMetadata(readJson(rs.getString("metadata")));
        product.setVersion(rs.getLong("version"));
        product.setPublishedAt(rs.getObject("published_at", OffsetDateTime.class));
        product.setDeletedAt(rs.getObject("deleted_at", OffsetDateTime.class));
        product.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        product.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return product;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid product metadata", e);
        }
    }

    private Map<String, Object> readJson(String value) throws SQLException {
        if (value == null || value.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid product metadata", e);
        }
    }

    private void validatePage(int limit, int offset) {
        if (limit < 1 || offset < 0) {
            throw new IllegalArgumentException(
                    "limit must be positive and offset must not be negative");
        }
    }
}
