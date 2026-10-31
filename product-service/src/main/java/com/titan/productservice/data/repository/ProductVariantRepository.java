package com.titan.productservice.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.productservice.data.entity.ProductVariantEntity;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductVariantRepository {
    private static final String COLUMNS =
            """
      id, product_id, sku, barcode, name, price, compare_at_price, currency,
      attributes, status, version, created_at, updated_at
      """;
    private static final String SELECT_BY_ID_SQL =
            "SELECT " + COLUMNS + " FROM product_variants WHERE id = ?";
    private static final String SELECT_BY_SKU_SQL =
            "SELECT " + COLUMNS + " FROM product_variants WHERE LOWER(sku) = LOWER(?)";
    private static final String SELECT_BY_PRODUCT_SQL =
            "SELECT " + COLUMNS + " FROM product_variants WHERE product_id = ? ORDER BY price, id";
    private static final String SELECT_ACTIVE_BY_PRODUCT_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM product_variants WHERE product_id = ? AND status = 'ACTIVE'"
                    + " ORDER BY price, id";
    private static final String SELECT_BY_PRODUCT_AND_ID_SQL =
            "SELECT " + COLUMNS + " FROM product_variants WHERE product_id = ? AND id = ?";
    private static final String INSERT_SQL =
            """
      INSERT INTO product_variants
        (id, product_id, sku, barcode, name, price, compare_at_price, currency,
         attributes, status, version)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
      """;
    private static final String UPDATE_SQL =
            """
      UPDATE product_variants
      SET sku = ?, barcode = ?, name = ?, price = ?,
        compare_at_price = ?, currency = ?, attributes = ?::jsonb,
        status = ?, version = version + 1, updated_at = NOW()
      WHERE id = ? AND product_id = ? AND version = ?
      """;
    private static final String DEACTIVATE_SQL =
            """
      UPDATE product_variants
      SET status = 'INACTIVE', version = version + 1, updated_at = clock_timestamp()
      WHERE id = ? AND product_id = ? AND version = ? AND status <> 'INACTIVE'
      """;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ProductRepository productRepository;
    private final RowMapper<ProductVariantEntity> rowMapper = this::mapVariant;

    public ProductVariantRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ProductRepository productRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.productRepository = productRepository;
    }

    @Transactional
    public void insert(ProductVariantEntity variant) {
        insert(variant, currentProductVersion(variant.getProductId()));
    }

    @Transactional
    public void insert(ProductVariantEntity variant, long expectedProductVersion) {
        productRepository.incrementVersion(variant.getProductId(), expectedProductVersion);
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
                    statement.setObject(1, variant.getId());
                    bindInsert(statement, variant);
                    return statement;
                });
    }

    @Transactional
    public int update(ProductVariantEntity variant) {
        return update(variant, currentProductVersion(variant.getProductId()));
    }

    @Transactional
    public int update(ProductVariantEntity variant, long expectedProductVersion) {
        productRepository.incrementVersion(variant.getProductId(), expectedProductVersion);
        int updated =
                jdbcTemplate.update(
                        connection -> {
                            PreparedStatement statement = connection.prepareStatement(UPDATE_SQL);
                            statement.setString(1, variant.getSku());
                            statement.setString(2, variant.getBarcode());
                            statement.setString(3, variant.getName());
                            statement.setBigDecimal(4, variant.getPrice());
                            statement.setBigDecimal(5, variant.getCompareAtPrice());
                            statement.setString(6, variant.getCurrency());
                            statement.setObject(7, json(variant.getAttributes()), Types.OTHER);
                            statement.setString(8, variant.getStatus());
                            statement.setObject(9, variant.getId());
                            statement.setObject(10, variant.getProductId());
                            statement.setLong(11, variant.getVersion());
                            return statement;
                        });
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Variant was changed or does not exist: " + variant.getId());
        }
        return updated;
    }

    public Optional<ProductVariantEntity> findById(UUID id) {
        return jdbcTemplate.query(SELECT_BY_ID_SQL, rowMapper, id).stream().findFirst();
    }

    public Optional<ProductVariantEntity> findBySku(String sku) {
        return jdbcTemplate.query(SELECT_BY_SKU_SQL, rowMapper, sku).stream().findFirst();
    }

    public List<ProductVariantEntity> findByProductId(UUID productId) {
        return jdbcTemplate.query(SELECT_BY_PRODUCT_SQL, rowMapper, productId);
    }

    public List<ProductVariantEntity> findActiveByProductId(UUID productId) {
        return jdbcTemplate.query(SELECT_ACTIVE_BY_PRODUCT_SQL, rowMapper, productId);
    }

    public Optional<ProductVariantEntity> findByProductIdAndId(UUID productId, UUID id) {
        return jdbcTemplate.query(SELECT_BY_PRODUCT_AND_ID_SQL, rowMapper, productId, id).stream()
                .findFirst();
    }

    @Transactional
    public int deleteById(UUID id) {
        ProductVariantEntity variant =
                findById(id)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Variant does not exist: " + id));
        return deactivateById(
                id,
                variant.getProductId(),
                variant.getVersion(),
                currentProductVersion(variant.getProductId()));
    }

    @Transactional
    public int deactivateById(
            UUID id, UUID productId, long expectedVariantVersion, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        int deactivated =
                jdbcTemplate.update(DEACTIVATE_SQL, id, productId, expectedVariantVersion);
        if (deactivated == 0) {
            throw new OptimisticLockingFailureException(
                    "Variant was changed, already inactive, or does not exist: " + id);
        }
        return deactivated;
    }

    @Transactional
    public int[] insertBatch(List<ProductVariantEntity> variants) {
        if (variants == null || variants.isEmpty()) {
            return new int[0];
        }
        int[] results = new int[variants.size()];
        for (int index = 0; index < variants.size(); index++) {
            insert(variants.get(index));
            results[index] = 1;
        }
        return results;
    }

    private long currentProductVersion(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM products WHERE id = ? AND deleted_at IS NULL FOR UPDATE",
                Long.class,
                productId);
    }

    private void bindInsert(PreparedStatement statement, ProductVariantEntity variant)
            throws SQLException {
        statement.setObject(2, variant.getProductId());
        statement.setString(3, variant.getSku());
        statement.setString(4, variant.getBarcode());
        statement.setString(5, variant.getName());
        statement.setBigDecimal(6, variant.getPrice());
        statement.setBigDecimal(7, variant.getCompareAtPrice());
        statement.setString(8, variant.getCurrency());
        statement.setObject(9, json(variant.getAttributes()), Types.OTHER);
        statement.setString(10, variant.getStatus());
        statement.setLong(11, variant.getVersion());
    }

    private ProductVariantEntity mapVariant(ResultSet rs, int row) throws SQLException {
        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setId(rs.getObject("id", UUID.class));
        variant.setProductId(rs.getObject("product_id", UUID.class));
        variant.setSku(rs.getString("sku"));
        variant.setBarcode(rs.getString("barcode"));
        variant.setName(rs.getString("name"));
        variant.setPrice(rs.getBigDecimal("price"));
        variant.setCompareAtPrice(rs.getBigDecimal("compare_at_price"));
        variant.setCurrency(rs.getString("currency"));
        variant.setAttributes(readJson(rs.getString("attributes")));
        variant.setStatus(rs.getString("status"));
        variant.setVersion(rs.getLong("version"));
        variant.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        variant.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return variant;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid variant attributes", e);
        }
    }

    private Map<String, Object> readJson(String value) throws SQLException {
        if (value == null || value.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid variant attributes", e);
        }
    }
}
