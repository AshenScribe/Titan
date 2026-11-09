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

package com.titan.product.orderservice.data.repository;

import com.titan.product.orderservice.data.entity.OrderItemEntity;
import com.titan.product.orderservice.data.exception.OrderPersistenceExceptionTranslator;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderItemRepository {

    private static final String COLUMNS =
            """
                    id, order_id, product_id, variant_id, product_name, variant_name,
                    sku, attributes, quantity, unit_price, position, created_at
                    """;

    private static final String TABLE_NAME = "order_items";

    private static final String INSERT_STATEMENT =
            """
            INSERT INTO %s (
                id, order_id, product_id, variant_id, product_name, variant_name,
                sku, attributes, quantity, unit_price, position, created_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
                    """
                    .formatted(TABLE_NAME);

    private static final String FIND_BY_ID =
            """
            SELECT %s FROM %s WHERE id = ?
                    """
                    .formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_ORDER_ID =
            """
                    SELECT %s
                    FROM %s
                    WHERE order_id = ?
                    ORDER BY position ASC, id ASC
                    """
                    .formatted(COLUMNS, TABLE_NAME);

    private final RowMapper<OrderItemEntity> orderItemMapper = this::mapOrderItem;
    private final JdbcTemplate jdbcTemplate;

    public OrderItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a new order item into the database.
     *
     * @param orderItemEntity the order item entity to insert
     */
    public void insert(OrderItemEntity orderItemEntity) {
        execute(
                () ->
                        jdbcTemplate.update(
                                INSERT_STATEMENT,
                                statement -> bindInsertStatement(statement, orderItemEntity)));
    }

    /**
     * Finds an order item by its unique identifier.
     *
     * @param id the unique order item ID
     * @return the matching order item, or empty if not found
     */
    public Optional<OrderItemEntity> findById(UUID id) {
        return execute(
                () -> jdbcTemplate.query(FIND_BY_ID, orderItemMapper, id).stream().findFirst());
    }

    /**
     * Finds all order items associated with a specific order.
     *
     * @param orderId the order ID
     * @return matching order items ordered by position ascending
     */
    public List<OrderItemEntity> findByOrderId(UUID orderId) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ORDER_ID, orderItemMapper, orderId));
    }

    private void bindInsertStatement(PreparedStatement statement, OrderItemEntity orderItemEntity)
            throws SQLException {

        int index = 1;

        statement.setObject(index++, orderItemEntity.id());
        statement.setObject(index++, orderItemEntity.orderId());
        statement.setObject(index++, orderItemEntity.productId());
        statement.setObject(index++, orderItemEntity.variantId());
        statement.setString(index++, orderItemEntity.productName());
        statement.setString(index++, orderItemEntity.variantName());
        statement.setString(index++, orderItemEntity.sku());

        setJsonb(statement, index++, orderItemEntity.attributes());

        statement.setInt(index++, orderItemEntity.quantity());
        statement.setBigDecimal(index++, orderItemEntity.unitPrice());
        statement.setShort(index++, orderItemEntity.position());
        statement.setObject(index, orderItemEntity.createdAt());
    }

    private void setJsonb(PreparedStatement statement, int index, String json) throws SQLException {

        if (json == null) {
            statement.setNull(index, Types.OTHER);
            return;
        }

        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        pgObject.setValue(json);

        statement.setObject(index, pgObject);
    }

    private OrderItemEntity mapOrderItem(ResultSet resultSet, int rowNumber) throws SQLException {

        return new OrderItemEntity(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("order_id", UUID.class),
                resultSet.getObject("product_id", UUID.class),
                resultSet.getObject("variant_id", UUID.class),
                resultSet.getString("product_name"),
                resultSet.getString("variant_name"),
                resultSet.getString("sku"),
                resultSet.getString("attributes"),
                resultSet.getInt("quantity"),
                resultSet.getBigDecimal("unit_price"),
                resultSet.getShort("position"),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            throw OrderPersistenceExceptionTranslator.translate(exception);
        }
    }
}
