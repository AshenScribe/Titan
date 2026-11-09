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

import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderStatus;
import com.titan.product.orderservice.data.exception.OrderPersistenceExceptionTranslator;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String COLUMNS =
            "id, user_id, status, currency, subtotal_amount, tax_amount, "
                    + "shipping_amount, discount_amount, total_amount, shipping_address, "
                    + "billing_address, customer_note, version, inventory_reserved_at, "
                    + "paid_at, confirmed_at, shipped_at, delivered_at, cancelled_at, "
                    + "refunded_at, created_at, updated_at";

    private static final String TABLE_NAME = "orders";

    private static final String INSERT_STATEMENT =
            """
            INSERT INTO %s (
                id, user_id, status, currency, subtotal_amount, tax_amount,
                shipping_amount, discount_amount, shipping_address, billing_address,
                customer_note, version, inventory_reserved_at, paid_at, confirmed_at,
                shipped_at, delivered_at, cancelled_at, refunded_at, created_at, updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
                    """
                    .formatted(TABLE_NAME);

    private static final String FIND_BY_ID =
            """
            SELECT %s FROM %s WHERE id = ?
                    """
                    .formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_STATUS =
            """
                    SELECT %s
                    FROM %s
                    WHERE status = ?::order_status
                    ORDER BY created_at DESC, id DESC
                    """
                    .formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_DATE_RANGE =
            """
                    SELECT %s
                    FROM %s
                    WHERE created_at BETWEEN ? AND ?
                    ORDER BY created_at DESC, id DESC
                    """
                    .formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_ID_FOR_USER =
            """
            SELECT %s FROM %s WHERE id = ? AND user_id = ?
                    """
                    .formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_PAGE_BY_USER =
            """
            SELECT o.*, COUNT(i.id) AS item_count
                    FROM orders o
                    LEFT JOIN order_items i ON i.order_id = o.id
                    WHERE o.user_id = ?
                    GROUP BY o.id
                    ORDER BY o.created_at DESC, o.id DESC
            LIMIT ? OFFSET ?
            """;

    private static final String COUNT_BY_USER = "SELECT COUNT(*) FROM orders WHERE user_id = ?";

    private static final String CANCEL =
            """
                    UPDATE orders
                    SET status = 'cancelled',
                        cancelled_at = ?,
                        updated_at = ?,
                        version = version + 1
                    WHERE id = ?
                      AND user_id = ?
                      AND status = 'pending'
            """;

    private final RowMapper<OrderEntity> orderMapper = this::mapOrder;
    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a new order into the database.
     *
     * @param orderEntity the order entity to insert
     */
    public void insert(OrderEntity orderEntity) {
        execute(
                () ->
                        jdbcTemplate.update(
                                INSERT_STATEMENT,
                                statement -> bindInsertStatement(statement, orderEntity)));
    }

    /**
     * Finds an order by its unique identifier.
     *
     * @param id the unique order ID
     * @return the matching order, or empty if not found
     */
    public Optional<OrderEntity> findById(UUID id) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ID, orderMapper, id).stream().findFirst());
    }

    /**
     * Finds an order belonging to a specific user.
     *
     * @param id the order ID
     * @param userId the user ID
     * @return the matching order, or empty if not found
     */
    public Optional<OrderEntity> findByIdForUser(UUID id, UUID userId) {
        return execute(
                () ->
                        jdbcTemplate.query(FIND_BY_ID_FOR_USER, orderMapper, id, userId).stream()
                                .findFirst());
    }

    /**
     * Retrieves a paginated list of orders belonging to a user.
     *
     * @param userId the user ID
     * @param page the zero-based page number
     * @param size the number of orders per page
     * @return the requested page of orders
     */
    public List<OrderPageRow> findPageByUser(UUID userId, int page, int size) {
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        if (page < 0) {
            throw new IllegalArgumentException("Page must be a non-negative integer");
        }

        long offset = (long) page * size;

        return execute(
                () ->
                        jdbcTemplate.query(
                                FIND_PAGE_BY_USER,
                                (resultSet, rowNumber) ->
                                        new OrderPageRow(
                                                mapOrder(resultSet, rowNumber),
                                                resultSet.getInt("item_count")),
                                userId,
                                size,
                                offset));
    }

    /**
     * Counts orders belonging to a user.
     *
     * @param userId the user ID
     * @return the number of orders
     */
    public long countByUser(UUID userId) {
        return execute(() -> jdbcTemplate.queryForObject(COUNT_BY_USER, Long.class, userId));
    }

    /**
     * Cancels a pending order belonging to a user.
     *
     * @param id the order ID
     * @param userId the user ID
     * @param cancelledAt the cancellation timestamp
     * @return the number of affected rows
     */
    public int cancel(UUID id, UUID userId, OffsetDateTime cancelledAt) {
        return execute(() -> jdbcTemplate.update(CANCEL, cancelledAt, cancelledAt, id, userId));
    }

    /**
     * Represents an order and its associated item count.
     *
     * @param order the order
     * @param itemCount the number of items
     */
    public record OrderPageRow(OrderEntity order, int itemCount) {}

    /**
     * Finds all orders matching the specified status.
     *
     * @param status the order status
     * @return matching orders ordered by creation time descending
     */
    public List<OrderEntity> findByStatus(OrderStatus status) {
        Objects.requireNonNull(status, "Status must not be null");

        return execute(() -> jdbcTemplate.query(FIND_BY_STATUS, orderMapper, status.getValue()));
    }

    /**
     * Finds orders created within the specified inclusive date range.
     *
     * @param startDate the start timestamp
     * @param endDate the end timestamp
     * @return matching orders ordered by creation time descending
     */
    public List<OrderEntity> findByDateRange(OffsetDateTime startDate, OffsetDateTime endDate) {

        Objects.requireNonNull(startDate, "Start date must not be null");
        Objects.requireNonNull(endDate, "End date must not be null");

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must not be after end date");
        }

        return execute(
                () -> jdbcTemplate.query(FIND_BY_DATE_RANGE, orderMapper, startDate, endDate));
    }

    private void bindInsertStatement(PreparedStatement statement, OrderEntity orderEntity)
            throws SQLException {

        int index = 1;

        statement.setObject(index++, orderEntity.id());
        statement.setObject(index++, orderEntity.userId());

        if (orderEntity.status() == null) {
            statement.setNull(index++, Types.OTHER);
        } else {
            statement.setObject(index++, orderEntity.status().getValue(), Types.OTHER);
        }

        statement.setString(index++, orderEntity.currency());
        statement.setBigDecimal(index++, orderEntity.subtotalAmount());
        statement.setBigDecimal(index++, orderEntity.taxAmount());
        statement.setBigDecimal(index++, orderEntity.shippingAmount());
        statement.setBigDecimal(index++, orderEntity.discountAmount());

        setJsonb(statement, index++, orderEntity.shippingAddress());
        setJsonb(statement, index++, orderEntity.billingAddress());

        statement.setString(index++, orderEntity.customerNote());
        statement.setLong(index++, orderEntity.version());

        statement.setObject(index++, orderEntity.inventoryReservedAt());
        statement.setObject(index++, orderEntity.paidAt());
        statement.setObject(index++, orderEntity.confirmedAt());
        statement.setObject(index++, orderEntity.shippedAt());
        statement.setObject(index++, orderEntity.deliveredAt());
        statement.setObject(index++, orderEntity.cancelledAt());
        statement.setObject(index++, orderEntity.refundedAt());
        statement.setObject(index++, orderEntity.createdAt());
        statement.setObject(index, orderEntity.updatedAt());
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

    private OrderEntity mapOrder(ResultSet resultSet, int rowNumber) throws SQLException {

        return new OrderEntity(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                OrderStatus.fromValue(resultSet.getString("status")),
                resultSet.getString("currency"),
                resultSet.getBigDecimal("subtotal_amount"),
                resultSet.getBigDecimal("tax_amount"),
                resultSet.getBigDecimal("shipping_amount"),
                resultSet.getBigDecimal("discount_amount"),
                resultSet.getBigDecimal("total_amount"),
                resultSet.getString("shipping_address"),
                resultSet.getString("billing_address"),
                resultSet.getString("customer_note"),
                resultSet.getLong("version"),
                resultSet.getObject("inventory_reserved_at", OffsetDateTime.class),
                resultSet.getObject("paid_at", OffsetDateTime.class),
                resultSet.getObject("confirmed_at", OffsetDateTime.class),
                resultSet.getObject("shipped_at", OffsetDateTime.class),
                resultSet.getObject("delivered_at", OffsetDateTime.class),
                resultSet.getObject("cancelled_at", OffsetDateTime.class),
                resultSet.getObject("refunded_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            throw OrderPersistenceExceptionTranslator.translate(exception);
        }
    }
}
