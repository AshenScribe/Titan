//------------------------------------------------------------------------------
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
//------------------------------------------------------------------------------

package com.titan.productservice.data.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.productservice.presentation.dto.BrandListQuery;
import com.titan.productservice.data.entity.BrandEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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

@Repository
public class BrandRepository {

    private static final String COLUMNS =
            "id, name, slug, description, website_url, logo_url, metadata, is_active, created_at, updated_at";
    private static final String SELECT_BY_ID_SQL =
            "SELECT " + COLUMNS + " FROM brands WHERE id = ?";
    private static final String SELECT_BY_SLUG_SQL =
            "SELECT " + COLUMNS + " FROM brands WHERE LOWER(slug) = LOWER(?)";
    private static final String SELECT_ACTIVE_SQL =
            "SELECT "
                    + COLUMNS
                    + " FROM brands WHERE is_active = TRUE ORDER BY id LIMIT ? OFFSET ?";
    private static final String INSERT_SQL =
            "INSERT INTO brands(id, name, slug, description, website_url, logo_url, metadata, is_active) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)";
    private static final String UPDATE_SQL =
            "UPDATE brands "
                    + "SET name = ?, slug = ?, description = ?, website_url = ?, logo_url = ?, metadata = ?::jsonb, is_active = ?, updated_at = NOW() "
                    + "WHERE id = ?";
    private static final String DELETE_SQL = "DELETE FROM brands WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<BrandEntity> rowMapper = this::mapBrand;

    public BrandRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insert(BrandEntity brandEntity) {
        jdbcTemplate.update(INSERT_SQL, statement -> {
            statement.setObject(1, brandEntity.getId());
            bindMutableFields(statement, brandEntity, 2);
        });
    }

    public int update(BrandEntity brandEntity) {
        return jdbcTemplate.update(UPDATE_SQL, statement -> {
            bindMutableFields(statement, brandEntity, 1);
            statement.setObject(8, brandEntity.getId());
        });
    }

    public Optional<BrandEntity> findById(UUID id) {
        List<BrandEntity> brands = jdbcTemplate.query(SELECT_BY_ID_SQL, rowMapper, id);
        return brands.stream().findFirst();
    }

    public Optional<BrandEntity> findBySlug(String slug) {
        List<BrandEntity> brands = jdbcTemplate.query(SELECT_BY_SLUG_SQL, rowMapper, slug);
        return brands.stream().findFirst();
    }

    public List<BrandEntity> findActive(int limit, int offset) {
        if (limit < 1 || offset < 0) {
            throw new IllegalArgumentException(
                    "limit must be positive and offset must not be negative");
        }
        return jdbcTemplate.query(SELECT_ACTIVE_SQL, rowMapper, limit, offset);
    }

    public BrandPage findPage(BrandListQuery query) {
        if (query.getPage() < 0 || query.getSize() < 1 || query.getSize() > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be 1..100");
        }
        StringBuilder where = new StringBuilder(" FROM brands WHERE 1 = 1");
        List<Object> parameters = new java.util.ArrayList<>();
        appendFilters(where, parameters, query);
        Integer total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)" + where, Integer.class, parameters.toArray());
        List<Object> pageParameters = new java.util.ArrayList<>(parameters);
        pageParameters.add(query.getSize());
        pageParameters.add(query.getPage() * query.getSize());
        List<BrandEntity> brands =
                jdbcTemplate.query(
                        "SELECT " + COLUMNS + where + " ORDER BY id LIMIT ? OFFSET ?",
                        rowMapper,
                        pageParameters.toArray());
        return new BrandPage(brands, total == null ? 0 : total);
    }

    private void appendFilters(StringBuilder where, List<Object> parameters, BrandListQuery query) {
        if (query.getActive() != null) {
            where.append(" AND is_active = ?");
            parameters.add(query.getActive());
        }
        appendTextFilter(where, parameters, "name", query.getName());
        appendTextFilter(where, parameters, "slug", query.getSlug());
    }

    private void appendTextFilter(
            StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" ILIKE ?");
            parameters.add("%" + value.trim() + "%");
        }
    }

    public record BrandPage(List<BrandEntity> brands, int totalElements) {

    }

    public int deleteById(UUID id) {
        return jdbcTemplate.update(DELETE_SQL, id);
    }

    public int[] insertBatch(List<BrandEntity> brands) {
        if (brands == null || brands.isEmpty()) {
            return new int[0];
        }
        return jdbcTemplate.batchUpdate(
                INSERT_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index)
                            throws SQLException {
                        BrandEntity brandEntity = brands.get(index);
                        statement.setObject(1, brandEntity.getId());
                        bindMutableFields(statement, brandEntity, 2);
                    }

                    @Override
                    public int getBatchSize() {
                        return brands.size();
                    }
                });
    }

    private void bindMutableFields(
            PreparedStatement statement, BrandEntity brandEntity, int startIndex)
            throws SQLException {
        statement.setString(startIndex, brandEntity.getName());
        statement.setString(startIndex + 1, brandEntity.getSlug());
        statement.setString(startIndex + 2, brandEntity.getDescription());
        statement.setString(startIndex + 3, brandEntity.getWebsiteUrl());
        statement.setString(startIndex + 4, brandEntity.getLogoUrl());
        statement.setObject(
                startIndex + 5, serializeMetadata(brandEntity.getMetadata()), Types.OTHER);
        statement.setBoolean(startIndex + 6, brandEntity.isActive());
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Brand metadata cannot be serialized", exception);
        }
    }

    private BrandEntity mapBrand(ResultSet resultSet, int rowNumber) throws SQLException {
        BrandEntity brandEntity = new BrandEntity();
        brandEntity.setId(resultSet.getObject("id", UUID.class));
        brandEntity.setName(resultSet.getString("name"));
        brandEntity.setSlug(resultSet.getString("slug"));
        brandEntity.setDescription(resultSet.getString("description"));
        brandEntity.setWebsiteUrl(resultSet.getString("website_url"));
        brandEntity.setLogoUrl(resultSet.getString("logo_url"));
        brandEntity.setMetadata(deserializeMetadata(resultSet.getString("metadata")));
        brandEntity.setActive(resultSet.getBoolean("is_active"));
        brandEntity.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class));
        brandEntity.setUpdatedAt(resultSet.getObject("updated_at", OffsetDateTime.class));
        return brandEntity;
    }

    private Map<String, Object> deserializeMetadata(String metadata) throws SQLException {
        if (metadata == null || metadata.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new SQLException("Brand metadata contains invalid JSON", exception);
        }
    }

}
