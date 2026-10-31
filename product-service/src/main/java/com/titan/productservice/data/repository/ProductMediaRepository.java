package com.titan.productservice.data.repository;

import com.titan.productservice.data.entity.ProductMediaEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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
            """
      INSERT INTO product_media
        (id, product_id, media_type, storage_key, alt_text, position, is_primary)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      """;
    private static final String UPDATE_SQL =
            """
      UPDATE product_media
      SET media_type = ?, storage_key = ?, alt_text = ?, position = ?, is_primary = ?
      WHERE id = ? AND product_id = ?
      """;
    private static final String DELETE_SQL =
            "DELETE FROM product_media WHERE id = ? AND product_id = ?";
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
                INSERT_SQL,
                media.getId(),
                media.getProductId(),
                media.getMediaType(),
                media.getStorageKey(),
                media.getAltText(),
                media.getPosition(),
                media.isPrimary());
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
                        UPDATE_SQL,
                        media.getMediaType(),
                        media.getStorageKey(),
                        media.getAltText(),
                        media.getPosition(),
                        media.isPrimary(),
                        media.getId(),
                        media.getProductId());
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "Media was changed or does not exist: " + media.getId());
        }
        return updated;
    }

    @Transactional
    public void insertReplacingPrimary(ProductMediaEntity media, long expectedProductVersion) {
        productRepository.incrementVersion(media.getProductId(), expectedProductVersion);
        if (media.isPrimary()) {
            jdbcTemplate.update(
                    "UPDATE product_media SET is_primary = FALSE WHERE product_id = ?",
                    media.getProductId());
        }
        jdbcTemplate.update(
                INSERT_SQL,
                media.getId(),
                media.getProductId(),
                media.getMediaType(),
                media.getStorageKey(),
                media.getAltText(),
                media.getPosition(),
                media.isPrimary());
    }

    @Transactional
    public int updateReplacingPrimary(ProductMediaEntity media, long expectedProductVersion) {
        productRepository.incrementVersion(media.getProductId(), expectedProductVersion);
        if (media.isPrimary()) {
            jdbcTemplate.update(
                    "UPDATE product_media SET is_primary = FALSE WHERE product_id = ?",
                    media.getProductId());
        }
        int updated =
                jdbcTemplate.update(
                        UPDATE_SQL,
                        media.getMediaType(),
                        media.getStorageKey(),
                        media.getAltText(),
                        media.getPosition(),
                        media.isPrimary(),
                        media.getId(),
                        media.getProductId());
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
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
                                () -> new IllegalArgumentException("Media does not exist: " + id));
        return deleteById(id, media.getProductId(), currentProductVersion(media.getProductId()));
    }

    @Transactional
    public int deleteById(UUID id, UUID productId, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        int deleted = jdbcTemplate.update(DELETE_SQL, id, productId);
        if (deleted == 0) {
            throw new OptimisticLockingFailureException(
                    "Media was changed or does not exist: " + id);
        }
        return deleted;
    }

    @Transactional
    public void reorder(UUID productId, List<UUID> mediaIds, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        jdbcTemplate.batchUpdate(
                "UPDATE product_media SET position = ? WHERE product_id = ? AND id = ?",
                mediaIds,
                mediaIds.size(),
                (statement, mediaId) -> {
                    int position = mediaIds.indexOf(mediaId);
                    statement.setInt(1, position);
                    statement.setObject(2, productId);
                    statement.setObject(3, mediaId);
                });
    }

    @Transactional
    public int setPrimary(UUID productId, UUID mediaId, long expectedProductVersion) {
        productRepository.incrementVersion(productId, expectedProductVersion);
        jdbcTemplate.update(
                "UPDATE product_media SET is_primary = FALSE WHERE product_id = ?", productId);
        int updated =
                jdbcTemplate.update(
                        "UPDATE product_media SET is_primary = TRUE WHERE product_id = ? AND id = ?",
                        productId,
                        mediaId);
        if (updated == 0) {
            throw new DataIntegrityViolationException("Media does not belong to product");
        }
        return updated;
    }

    @Transactional
    public int[] insertBatch(List<ProductMediaEntity> mediaItems) {
        if (mediaItems == null || mediaItems.isEmpty()) {
            return new int[0];
        }
        int[] results = new int[mediaItems.size()];
        for (int index = 0; index < mediaItems.size(); index++) {
            insert(mediaItems.get(index));
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
