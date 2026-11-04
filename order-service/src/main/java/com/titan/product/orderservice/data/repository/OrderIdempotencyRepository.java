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

package com.titan.product.orderservice.data.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.titan.product.orderservice.data.entity.OrderIdempotencyEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderIdempotencyRepository {
    private static final String COLUMNS = """
        user_id, idempotency_key, request_hash, order_id, created_at, expires_at\
        """;

    private static final String tableName = "order_idempotency";

    private static final String insertStatement = """
            INSERT INTO %s (
                user_id, idempotency_key, request_hash, order_id, created_at, expires_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?
            )
            """.formatted(tableName);

    private static final String findByKey = """
            SELECT %s FROM %s WHERE user_id = ? AND idempotency_key = ?
            """.formatted(COLUMNS, tableName);

    private static final String deleteExpired = """
            DELETE FROM %s WHERE expires_at < ?
            """.formatted(tableName);

    private final RowMapper<OrderIdempotencyEntity> idempotencyMapper = this::idempotencyMapper;
    private final JdbcTemplate jdbcTemplate;

    public OrderIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a new order idempotency record into the database.
     *
     * @param entity the order idempotency entity to insert
     */
    public void insert(OrderIdempotencyEntity entity) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(insertStatement);
            bindFields(statement, entity, 1);
            return statement;
        });
    }

    /**
     * Finds an order idempotency record by user ID and idempotency key.
     *
     * @param userId the user ID
     * @param idempotencyKey the idempotency key
     * @return an Optional containing the idempotency record if found, or empty otherwise
     */
    public Optional<OrderIdempotencyEntity> findByKey(UUID userId, String idempotencyKey) {
        List<OrderIdempotencyEntity> results = jdbcTemplate.query(findByKey, idempotencyMapper, userId, idempotencyKey);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Deletes all expired idempotency records older than the specified timestamp.
     *
     * @param now the cutoff timestamp
     * @return the number of deleted records
     */
    public int deleteExpired(OffsetDateTime now) {
        return jdbcTemplate.update(deleteExpired, now);
    }

    private void bindFields(PreparedStatement statement, OrderIdempotencyEntity entity, int startIndex) throws SQLException {
        statement.setObject(startIndex++, entity.userId());
        statement.setString(startIndex++, entity.idempotencyKey());
        statement.setBytes(startIndex++, entity.requestHash());
        statement.setObject(startIndex++, entity.orderId());
        statement.setObject(startIndex++, entity.createdAt());
        statement.setObject(startIndex, entity.expiresAt());
    }

    private OrderIdempotencyEntity idempotencyMapper(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OrderIdempotencyEntity(
            resultSet.getObject("user_id", UUID.class),
            resultSet.getString("idempotency_key"),
            resultSet.getBytes("request_hash"),
            resultSet.getObject("order_id", UUID.class),
            resultSet.getObject("created_at", OffsetDateTime.class),
            resultSet.getObject("expires_at", OffsetDateTime.class)
        );
    }
}