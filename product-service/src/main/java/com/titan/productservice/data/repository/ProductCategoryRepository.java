package com.titan.productservice.data.repository;

import com.titan.productservice.data.entity.ProductCategoryEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductCategoryRepository {
    private static final String COLUMNS = "product_id, category_id, is_primary, created_at";
    private static final String SELECT_BY_PRODUCT_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM product_categories WHERE product_id = ? ORDER BY is_primary DESC, category_id";
    private static final String SELECT_BY_CATEGORY_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM product_categories WHERE category_id = ? ORDER BY product_id";
    private static final String INSERT_SQL =
            """
      INSERT INTO product_categories (product_id, category_id, is_primary)
      VALUES (?, ?, ?)
      """;
    private static final String UPDATE_SQL =
            """
      UPDATE product_categories SET is_primary = ?
      WHERE product_id = ? AND category_id = ?
      """;
    private static final String DELETE_SQL =
            "DELETE FROM product_categories WHERE product_id = ? AND category_id = ?";
    private static final RowMapper<ProductCategoryEntity> ROW_MAPPER =
            ProductCategoryRepository::map;
    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;

    public ProductCategoryRepository(
            JdbcTemplate jdbcTemplate, ProductRepository productRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.productRepository = productRepository;
    }

    @Transactional
    public void insert(ProductCategoryEntity relation) {
        insert(relation, currentProductVersion(relation.getProductId()));
    }

    @Transactional
    public void insert(ProductCategoryEntity relation, long expectedProductVersion) {
        productRepository.incrementVersion(relation.getProductId(), expectedProductVersion);
        jdbcTemplate.update(
                INSERT_SQL,
                relation.getProductId(),
                relation.getCategoryId(),
                relation.isPrimary());
    }

    @Transactional
    public void insertReplacingPrimary(
            ProductCategoryEntity relation, long expectedProductVersion) {
        productRepository.incrementVersion(relation.getProductId(), expectedProductVersion);
        if (relation.isPrimary()) {
            jdbcTemplate.update(
                    "UPDATE product_categories SET is_primary = FALSE WHERE product_id = ?",
                    relation.getProductId());
        }
        jdbcTemplate.update(
                INSERT_SQL,
                relation.getProductId(),
                relation.getCategoryId(),
                relation.isPrimary());
    }

    @Transactional
    public int update(ProductCategoryEntity relation) {
        return update(relation, currentProductVersion(relation.getProductId()));
    }

    @Transactional
    public int update(ProductCategoryEntity relation, long expectedProductVersion) {
        productRepository.incrementVersion(relation.getProductId(), expectedProductVersion);
        int updated =
                jdbcTemplate.update(
                        UPDATE_SQL,
                        relation.isPrimary(),
                        relation.getProductId(),
                        relation.getCategoryId());
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Product category assignment does not exist");
        }
        return updated;
    }

    @Transactional
    public int delete(UUID productId, UUID categoryId) {
        return delete(productId, categoryId, currentProductVersion(productId));
    }

    @Transactional
    public int delete(UUID productId, UUID categoryId, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        int deleted = jdbcTemplate.update(DELETE_SQL, productId, categoryId);
        if (deleted == 0) {
            throw new OptimisticLockingFailureException(
                    "Product category assignment does not exist");
        }
        return deleted;
    }

    @Transactional
    public void replace(
            UUID productId, List<ProductCategoryEntity> relations, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        jdbcTemplate.update("DELETE FROM product_categories WHERE product_id = ?", productId);
        for (ProductCategoryEntity relation : relations) {
            jdbcTemplate.update(
                    INSERT_SQL,
                    relation.getProductId(),
                    relation.getCategoryId(),
                    relation.isPrimary());
        }
    }

    @Transactional
    public int setPrimary(UUID productId, UUID categoryId, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        jdbcTemplate.update(
                "UPDATE product_categories SET is_primary = FALSE WHERE product_id = ?", productId);
        int updated =
                jdbcTemplate.update(
                        "UPDATE product_categories SET is_primary = TRUE"
                                + " WHERE product_id = ? AND category_id = ?",
                        productId,
                        categoryId);
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Product category assignment does not exist");
        }
        return updated;
    }

    public List<ProductCategoryEntity> findByProductId(UUID productId) {
        return jdbcTemplate.query(SELECT_BY_PRODUCT_SQL, ROW_MAPPER, productId);
    }

    public List<ProductCategoryEntity> findByCategoryId(UUID categoryId) {
        return jdbcTemplate.query(SELECT_BY_CATEGORY_SQL, ROW_MAPPER, categoryId);
    }

    @Transactional
    public int[] insertBatch(List<ProductCategoryEntity> relations) {
        if (relations == null || relations.isEmpty()) {
            return new int[0];
        }
        int[] results = new int[relations.size()];
        for (int index = 0; index < relations.size(); index++) {
            insert(relations.get(index));
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

    private static ProductCategoryEntity map(ResultSet rs, int row) throws SQLException {
        ProductCategoryEntity relation = new ProductCategoryEntity();
        relation.setProductId(rs.getObject("product_id", UUID.class));
        relation.setCategoryId(rs.getObject("category_id", UUID.class));
        relation.setPrimary(rs.getBoolean("is_primary"));
        relation.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return relation;
    }
}
