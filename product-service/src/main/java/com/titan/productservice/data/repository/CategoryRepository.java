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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.productservice.data.entity.CategoryEntity;
import com.titan.productservice.presentation.dto.CategoryListQuery;
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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepository {

    private static final String COLUMNS =
            """
      id, parent_id, name, slug, description, metadata,
      sort_order, is_active, created_at, updated_at
      """;

    private static final String SELECT_BY_ID_SQL =
            "SELECT " + COLUMNS + " FROM categories WHERE id = ?";
    private static final String SELECT_BY_SLUG_SQL =
            "SELECT " + COLUMNS + " FROM categories WHERE LOWER(slug) = LOWER(?)";
    private static final String SELECT_ACTIVE_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM categories WHERE is_active = TRUE ORDER BY sort_order, id LIMIT ? OFFSET ?";
    private static final String SELECT_CHILDREN_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM categories WHERE parent_id IS NOT DISTINCT FROM ?"
                    + " ORDER BY sort_order, id LIMIT ? OFFSET ?";
    private static final String INSERT_SQL =
            """
      INSERT INTO categories
        (id, parent_id, name, slug, description, metadata, sort_order, is_active)
      VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
      """;
    private static final String UPDATE_SQL =
            """
      UPDATE categories
      SET parent_id = ?, name = ?, slug = ?, description = ?, metadata = ?::jsonb,
        sort_order = ?, is_active = ?, updated_at = NOW()
      WHERE id = ?
      """;
    private static final String DELETE_SQL = "DELETE FROM categories WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<CategoryEntity> rowMapper = this::mapCategory;

    public CategoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insert(CategoryEntity category) {
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(INSERT_SQL);
                    statement.setObject(1, category.getId());
                    bindMutableFields(statement, category, 2);
                    return statement;
                });
    }

    public int update(CategoryEntity category) {
        return jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(UPDATE_SQL);
                    bindMutableFields(statement, category, 1);
                    statement.setObject(8, category.getId());
                    return statement;
                });
    }

    public Optional<CategoryEntity> findById(UUID id) {
        return jdbcTemplate.query(SELECT_BY_ID_SQL, rowMapper, id).stream().findFirst();
    }

    public Optional<CategoryEntity> findBySlug(String slug) {
        return jdbcTemplate.query(SELECT_BY_SLUG_SQL, rowMapper, slug).stream().findFirst();
    }

    public List<CategoryEntity> findByIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM categories WHERE id IN (" + placeholders + ")",
                rowMapper,
                ids.toArray());
    }

    public List<CategoryEntity> findActive(int limit, int offset) {
        validatePage(limit, offset);
        return jdbcTemplate.query(SELECT_ACTIVE_SQL, rowMapper, limit, offset);
    }

    public List<CategoryEntity> findChildren(UUID parentId, int limit, int offset) {
        validatePage(limit, offset);
        return jdbcTemplate.query(SELECT_CHILDREN_SQL, rowMapper, parentId, limit, offset);
    }

    public CategoryPage findPage(CategoryListQuery query) {
        if (query.getPage() < 0 || query.getSize() < 1 || query.getSize() > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be 1..100");
        }
        StringBuilder where = new StringBuilder(" FROM categories WHERE 1 = 1");
        List<Object> parameters = new java.util.ArrayList<>();
        appendFilters(where, parameters, query);
        Integer total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)" + where, Integer.class, parameters.toArray());
        List<Object> pageParameters = new java.util.ArrayList<>(parameters);
        pageParameters.add(query.getSize());
        pageParameters.add(query.getPage() * query.getSize());
        List<CategoryEntity> categories =
                jdbcTemplate.query(
                        "SELECT " + COLUMNS + where + " ORDER BY sort_order, id LIMIT ? OFFSET ?",
                        rowMapper,
                        pageParameters.toArray());
        return new CategoryPage(categories, total == null ? 0 : total);
    }

    private void appendFilters(
            StringBuilder where, List<Object> parameters, CategoryListQuery query) {
        if (query.getActive() != null) {
            where.append(" AND is_active = ?");
            parameters.add(query.getActive());
        }
        if (query.getParentId() != null) {
            where.append(" AND parent_id = ?");
            parameters.add(query.getParentId());
        }
        if (query.getName() != null && !query.getName().isBlank()) {
            where.append(" AND name ILIKE ?");
            parameters.add("%" + query.getName().trim() + "%");
        }
    }

    public List<CategoryEntity> findAllHierarchy() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM categories ORDER BY sort_order, id", rowMapper);
    }

    public List<CategoryEntity> findDescendants(UUID categoryId) {
        return jdbcTemplate.query(
                """
                WITH RECURSIVE descendants AS (
                    SELECT id, parent_id, name, slug, description, metadata,
                           sort_order, is_active, created_at, updated_at
                    FROM categories WHERE parent_id = ?
                    UNION ALL
                    SELECT c.id, c.parent_id, c.name, c.slug, c.description, c.metadata,
                           c.sort_order, c.is_active, c.created_at, c.updated_at
                    FROM categories c JOIN descendants d ON c.parent_id = d.id
                )
                SELECT id, parent_id, name, slug, description, metadata,
                       sort_order, is_active, created_at, updated_at
                FROM descendants ORDER BY sort_order, id
                """,
                rowMapper,
                categoryId);
    }

    public record CategoryPage(List<CategoryEntity> categories, int totalElements) {}

    public int deleteById(UUID id) {
        return jdbcTemplate.update(DELETE_SQL, id);
    }

    public int[] insertBatch(List<CategoryEntity> categories) {
        if (categories == null || categories.isEmpty()) {
            return new int[0];
        }
        return jdbcTemplate.batchUpdate(
                INSERT_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        CategoryEntity category = categories.get(index);
                        statement.setObject(1, category.getId());
                        bindMutableFields(statement, category, 2);
                    }

                    @Override
                    public int getBatchSize() {
                        return categories.size();
                    }
                });
    }

    private void bindMutableFields(
            PreparedStatement statement, CategoryEntity category, int startIndex)
            throws SQLException {
        statement.setObject(startIndex, category.getParentId());
        statement.setString(startIndex + 1, category.getName());
        statement.setString(startIndex + 2, category.getSlug());
        statement.setString(startIndex + 3, category.getDescription());
        statement.setObject(startIndex + 4, serializeMetadata(category.getMetadata()), Types.OTHER);
        statement.setInt(startIndex + 5, category.getSortOrder());
        statement.setBoolean(startIndex + 6, category.isActive());
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Category metadata cannot be serialized", exception);
        }
    }

    private CategoryEntity mapCategory(ResultSet resultSet, int rowNumber) throws SQLException {
        CategoryEntity category = new CategoryEntity();
        category.setId(resultSet.getObject("id", UUID.class));
        category.setParentId(resultSet.getObject("parent_id", UUID.class));
        category.setName(resultSet.getString("name"));
        category.setSlug(resultSet.getString("slug"));
        category.setDescription(resultSet.getString("description"));
        category.setMetadata(deserializeMetadata(resultSet.getString("metadata")));
        category.setSortOrder(resultSet.getInt("sort_order"));
        category.setActive(resultSet.getBoolean("is_active"));
        category.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class));
        category.setUpdatedAt(resultSet.getObject("updated_at", OffsetDateTime.class));
        return category;
    }

    private Map<String, Object> deserializeMetadata(String metadata) throws SQLException {
        if (metadata == null || metadata.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new SQLException("Category metadata contains invalid JSON", exception);
        }
    }

    private void validatePage(int limit, int offset) {
        if (limit < 1 || offset < 0) {
            throw new IllegalArgumentException(
                    "limit must be positive and offset must not be negative");
        }
    }
}
