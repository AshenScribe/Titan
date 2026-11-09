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

import com.titan.productservice.data.entity.ProductCategoryEntity;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
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
    private static final String UPDATE_SQL =
            "UPDATE product_categories SET is_primary = ? WHERE product_id = ? AND category_id = ?";
    private static final String INSERT_SQL =
            "INSERT INTO product_categories (product_id, category_id, is_primary) VALUES (?, ?, ?)";
    private static final String DELETE_SQL =
            "DELETE FROM product_categories WHERE product_id = ? AND category_id = ?";
    private static final String CLEAR_PRIMARY_SQL =
            "UPDATE product_categories SET is_primary = FALSE WHERE product_id = ?";
    private static final String SET_PRIMARY_SQL =
            "UPDATE product_categories SET is_primary = TRUE WHERE product_id = ? AND category_id = ?";
    private static final String DELETE_BY_PRODUCT_SQL =
            "DELETE FROM product_categories WHERE product_id = ?";
    private static final String SELECT_VERSION_SQL =
            "SELECT version FROM products WHERE id = ? AND deleted_at IS NULL FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final RowMapper<ProductCategoryEntity> rowMapper = ProductCategoryRepository::map;

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
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
                    bindInsert(statement, relation);
                    return statement;
                });
    }

    @Transactional
    public void insertReplacingPrimary(
            ProductCategoryEntity relation, long expectedProductVersion) {
        productRepository.incrementVersion(relation.getProductId(), expectedProductVersion);
        if (relation.isPrimary()) {
            jdbcTemplate.update(CLEAR_PRIMARY_SQL, relation.getProductId());
        }
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
                    bindMutableFields(statement, relation, 1);
                    return statement;
                });
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
                        connection -> {
                            PreparedStatement statement = connection.prepareStatement(UPDATE_SQL);
                            bindUpdate(statement, relation);
                            return statement;
                        });
        if (updated == 0) {
            throw new ProductConcurrentModificationException(
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
            throw new ProductConcurrentModificationException(
                    "Product category assignment does not exist");
        }
        return deleted;
    }

    @Transactional
    public void replace(
            UUID productId, List<ProductCategoryEntity> relations, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        jdbcTemplate.update(DELETE_BY_PRODUCT_SQL, productId);
        if (relations == null || relations.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        bindMutableFields(statement, relations.get(index), 1);
                    }

                    @Override
                    public int getBatchSize() {
                        return relations.size();
                    }
                });
    }

    @Transactional
    public int setPrimary(UUID productId, UUID categoryId, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        jdbcTemplate.update(CLEAR_PRIMARY_SQL, productId);
        int updated = jdbcTemplate.update(SET_PRIMARY_SQL, productId, categoryId);
        if (updated == 0) {
            throw new ProductConcurrentModificationException(
                    "Product category assignment does not exist");
        }
        return updated;
    }

    public List<ProductCategoryEntity> findByProductId(UUID productId) {
        return jdbcTemplate.query(SELECT_BY_PRODUCT_SQL, rowMapper, productId);
    }

    public List<ProductCategoryEntity> findByCategoryId(UUID categoryId) {
        return jdbcTemplate.query(SELECT_BY_CATEGORY_SQL, rowMapper, categoryId);
    }

    @Transactional
    public int[] insertBatch(List<ProductCategoryEntity> relations) {
        if (relations == null || relations.isEmpty()) {
            return new int[0];
        }
        List<UUID> productIds =
                relations.stream().map(ProductCategoryEntity::getProductId).distinct().toList();
        if (productIds.size() != 1) {
            throw new IllegalArgumentException(
                    "Batch insert must target a single product: " + productIds);
        }
        UUID productId = productIds.getFirst();
        productRepository.incrementVersion(productId, currentProductVersion(productId));
        return jdbcTemplate.batchUpdate(
                INSERT_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        bindMutableFields(statement, relations.get(index), 1);
                    }

                    @Override
                    public int getBatchSize() {
                        return relations.size();
                    }
                });
    }

    private long currentProductVersion(UUID productId) {
        return jdbcTemplate.queryForObject(SELECT_VERSION_SQL, Long.class, productId);
    }

    private void bindMutableFields(
            PreparedStatement statement, ProductCategoryEntity relation, int startIndex)
            throws SQLException {
        statement.setObject(startIndex, relation.getProductId());
        statement.setObject(startIndex + 1, relation.getCategoryId());
        statement.setBoolean(startIndex + 2, relation.isPrimary());
    }

    private static ProductCategoryEntity map(ResultSet resultSet, int rowNumber)
            throws SQLException {
        ProductCategoryEntity relation = new ProductCategoryEntity();
        relation.setProductId(resultSet.getObject("product_id", UUID.class));
        relation.setCategoryId(resultSet.getObject("category_id", UUID.class));
        relation.setPrimary(resultSet.getBoolean("is_primary"));
        relation.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class));
        return relation;
    }

    private void bindInsert(PreparedStatement statement, ProductCategoryEntity relation)
            throws SQLException {
        statement.setObject(1, relation.getProductId());
        statement.setObject(2, relation.getCategoryId());
        statement.setBoolean(3, relation.isPrimary());
    }

    private void bindUpdate(PreparedStatement statement, ProductCategoryEntity relation)
            throws SQLException {
        statement.setBoolean(1, relation.isPrimary());
        statement.setObject(2, relation.getProductId());
        statement.setObject(3, relation.getCategoryId());
    }
}
