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

import com.titan.productservice.data.entity.ProductMediaEntity;
import com.titan.productservice.data.exception.ProductConcurrentModificationException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductMediaRepository {

    private static final String COLUMNS =
            "id, product_id, media_type, storage_key, alt_text, position, is_primary, created_at";
    private static final String SELECT_BY_ID_SQL =
            "SELECT " + COLUMNS + " FROM product_media WHERE id = ?";
    private static final String SELECT_BY_PRODUCT_SQL =
            "SELECT " + COLUMNS + " FROM product_media WHERE product_id = ? ORDER BY position, id";
    private static final String INSERT_SQL =
            "INSERT INTO product_media(id, product_id, media_type, storage_key, alt_text, position, is_primary) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL =
            "UPDATE product_media "
                    + "SET media_type = ?, storage_key = ?, alt_text = ?, position = ?, is_primary = ? "
                    + "WHERE id = ? AND product_id = ?";
    private static final String DELETE_SQL =
            "DELETE FROM product_media WHERE id = ? AND product_id = ?";
    private static final String CLEAR_PRIMARY_SQL =
            "UPDATE product_media SET is_primary = FALSE WHERE product_id = ?";
    private static final String SET_PRIMARY_SQL =
            "UPDATE product_media SET is_primary = TRUE WHERE product_id = ? AND id = ?";
    private static final String REORDER_SQL =
            "UPDATE product_media SET position = ? WHERE product_id = ? AND id = ?";
    private static final String SELECT_VERSION_SQL =
            "SELECT version FROM products WHERE id = ? AND deleted_at IS NULL FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final RowMapper<ProductMediaEntity> rowMapper = ProductMediaRepository::map;

    public ProductMediaRepository(JdbcTemplate jdbcTemplate, ProductRepository productRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.productRepository = productRepository;
    }

    @Transactional
    public void insert(ProductMediaEntity media) {
        insert(media, currentProductVersion(media.getProductId()));
    }

    @Transactional
    public void insert(ProductMediaEntity media, long expectedProductVersion) {
        productRepository.incrementVersion(media.getProductId(), expectedProductVersion);
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
                    bindInsert(statement, media);
                    return statement;
                });
    }

    @Transactional
    public int update(ProductMediaEntity media) {
        return update(media, currentProductVersion(media.getProductId()));
    }

    @Transactional
    public int update(ProductMediaEntity media, long expectedProductVersion) {
        productRepository.incrementVersion(media.getProductId(), expectedProductVersion);
        int updated =
                jdbcTemplate.update(
                        connection -> {
                            PreparedStatement statement = connection.prepareStatement(UPDATE_SQL);
                            bindUpdate(statement, media);
                            return statement;
                        });
        if (updated == 0) {
            throw new ProductConcurrentModificationException(
                    "Media was changed or does not exist: " + media.getId());
        }
        return updated;
    }

    @Transactional
    public void insertReplacingPrimary(ProductMediaEntity media, long expectedProductVersion) {
        productRepository.incrementVersion(media.getProductId(), expectedProductVersion);
        if (media.isPrimary()) {
            jdbcTemplate.update(CLEAR_PRIMARY_SQL, media.getProductId());
        }
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
                    bindInsert(statement, media);
                    return statement;
                });
    }

    @Transactional
    public int updateReplacingPrimary(ProductMediaEntity media, long expectedProductVersion) {
        productRepository.incrementVersion(media.getProductId(), expectedProductVersion);
        if (media.isPrimary()) {
            jdbcTemplate.update(CLEAR_PRIMARY_SQL, media.getProductId());
        }
        int updated =
                jdbcTemplate.update(
                        connection -> {
                            PreparedStatement statement = connection.prepareStatement(UPDATE_SQL);
                            bindUpdate(statement, media);
                            return statement;
                        });
        if (updated == 0) {
            throw new ProductConcurrentModificationException(
                    "Media was changed or does not exist: " + media.getId());
        }
        return updated;
    }

    public Optional<ProductMediaEntity> findById(UUID id) {
        return jdbcTemplate.query(SELECT_BY_ID_SQL, rowMapper, id).stream().findFirst();
    }

    public List<ProductMediaEntity> findByProductId(UUID productId) {
        return jdbcTemplate.query(SELECT_BY_PRODUCT_SQL, rowMapper, productId);
    }

    @Transactional
    public int deleteById(UUID id) {
        ProductMediaEntity media =
                findById(id)
                        .orElseThrow(
                                () ->
                                        new ProductConcurrentModificationException(
                                                "Media does not exist: " + id));
        return deleteById(id, media.getProductId(), currentProductVersion(media.getProductId()));
    }

    @Transactional
    public int deleteById(UUID id, UUID productId, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        int deleted = jdbcTemplate.update(DELETE_SQL, id, productId);
        if (deleted == 0) {
            throw new ProductConcurrentModificationException(
                    "Media was changed or does not exist: " + id);
        }
        return deleted;
    }

    @Transactional
    public void reorder(UUID productId, List<UUID> mediaIds, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                REORDER_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        statement.setInt(1, index);
                        statement.setObject(2, productId);
                        statement.setObject(3, mediaIds.get(index));
                    }

                    @Override
                    public int getBatchSize() {
                        return mediaIds.size();
                    }
                });
    }

    @Transactional
    public int setPrimary(UUID productId, UUID mediaId, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        jdbcTemplate.update(CLEAR_PRIMARY_SQL, productId);
        int updated = jdbcTemplate.update(SET_PRIMARY_SQL, productId, mediaId);
        if (updated == 0) {
            throw new ProductConcurrentModificationException(
                    "Media does not belong to product: " + mediaId);
        }
        return updated;
    }

    @Transactional
    public int[] insertBatch(List<ProductMediaEntity> mediaItems) {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return new int[0];
        }
        List<UUID> productIds =
                mediaItems.stream().map(ProductMediaEntity::getProductId).distinct().toList();
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
                        bindInsert(statement, mediaItems.get(index));
                    }

                    @Override
                    public int getBatchSize() {
                        return mediaItems.size();
                    }
                });
    }

    private long currentProductVersion(UUID productId) {
        List<Long> versions =
                jdbcTemplate.query(
                        SELECT_VERSION_SQL, (rs, row) -> rs.getLong("version"), productId);
        if (versions.isEmpty()) {
            throw new ProductConcurrentModificationException(
                    "Product aggregate does not exist or is deleted: " + productId);
        }
        return versions.getFirst();
    }

    private void bindInsert(PreparedStatement statement, ProductMediaEntity media)
            throws SQLException {
        statement.setObject(1, media.getId());
        statement.setObject(2, media.getProductId());
        statement.setString(3, media.getMediaType());
        statement.setString(4, media.getStorageKey());
        statement.setString(5, media.getAltText());
        statement.setInt(6, media.getPosition());
        statement.setBoolean(7, media.isPrimary());
    }

    private void bindUpdate(PreparedStatement statement, ProductMediaEntity media)
            throws SQLException {
        statement.setString(1, media.getMediaType());
        statement.setString(2, media.getStorageKey());
        statement.setString(3, media.getAltText());
        statement.setInt(4, media.getPosition());
        statement.setBoolean(5, media.isPrimary());
        statement.setObject(6, media.getId());
        statement.setObject(7, media.getProductId());
    }

    private static ProductMediaEntity map(ResultSet rs, int row) throws SQLException {
        ProductMediaEntity media = new ProductMediaEntity();
        media.setId(rs.getObject("id", UUID.class));
        media.setProductId(rs.getObject("product_id", UUID.class));
        media.setMediaType(rs.getString("media_type"));
        media.setStorageKey(rs.getString("storage_key"));
        media.setAltText(rs.getString("alt_text"));
        media.setPosition(rs.getInt("position"));
        media.setPrimary(rs.getBoolean("is_primary"));
        media.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return media;
    }
}
