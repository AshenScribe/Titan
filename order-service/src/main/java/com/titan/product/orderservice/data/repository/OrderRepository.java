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

import com.titan.product.orderservice.data.entity.OrderEntity;
import com.titan.product.orderservice.data.entity.OrderStatus;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
	private static final String COLUMNS = """
        id, user_id, status, currency, subtotal_amount, tax_amount, \
        shipping_amount, discount_amount, total_amount, shipping_address, \
        billing_address, customer_note, version, inventory_reserved_at, \
        paid_at, confirmed_at, shipped_at, delivered_at, cancelled_at, \
        refunded_at, created_at, updated_at\
        """;

	private static final String tableName = "orders";

	private static final String insertStatement = """
            INSERT INTO %s (
                id, user_id, status, currency, subtotal_amount, tax_amount,
                shipping_amount, discount_amount, shipping_address, billing_address,
                customer_note, version, inventory_reserved_at, paid_at, confirmed_at,
                shipped_at, delivered_at, cancelled_at, refunded_at, created_at, updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """.formatted(tableName);

	private static final String findById = """
            SELECT %s FROM %s WHERE id = ?
            """.formatted(COLUMNS, tableName);

	private static final String findByStatus = """
        SELECT %s FROM %s WHERE status = ?::order_status ORDER BY created_at DESC
        """.formatted(COLUMNS, tableName);

	private static final String findByDateRange = """
        SELECT %s FROM %s WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC
        """.formatted(COLUMNS, tableName);

	private static final String findByIdForUser = """
            SELECT %s FROM %s WHERE id = ? AND user_id = ?
            """.formatted(COLUMNS, tableName);

	private static final String findPageByUser = """
            SELECT o.*, COUNT(i.id) AS item_count
            FROM orders o LEFT JOIN order_items i ON i.order_id = o.id
            WHERE o.user_id = ? GROUP BY o.id ORDER BY o.created_at DESC, o.id DESC
            LIMIT ? OFFSET ?
            """;

	private static final String countByUser = "SELECT COUNT(*) FROM orders WHERE user_id = ?";

	private static final String cancel = """
            UPDATE orders SET status = 'cancelled', cancelled_at = ?, updated_at = ?, version = version + 1
            WHERE id = ? AND user_id = ? AND status = 'pending'
            """;

	private final RowMapper<OrderEntity> orderMapper = this::orderMapper;
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
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(insertStatement);
			statement.setObject(1, orderEntity.id());
			bindFields(statement, orderEntity, 2);
			return statement;
		});
	}

	/**
	 * Finds an order by its unique identifier.
	 *
	 * @param id the unique order ID
	 * @return an Optional containing the order if found, or empty otherwise
	 */
	public Optional<OrderEntity> findById(UUID id) {
		List<OrderEntity> results = jdbcTemplate.query(findById, orderMapper, id);
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	public Optional<OrderEntity> findByIdForUser(UUID id, UUID userId) {
		List<OrderEntity> results = jdbcTemplate.query(findByIdForUser, orderMapper, id, userId);
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	public List<OrderPageRow> findPageByUser(UUID userId, int page, int size) {
		return jdbcTemplate.query(findPageByUser, (resultSet, rowNumber) ->
				new OrderPageRow(orderMapper.mapRow(resultSet, rowNumber), resultSet.getInt("item_count")),
				userId, size, (long) page * size);
	}

	public long countByUser(UUID userId) {
		return jdbcTemplate.queryForObject(countByUser, Long.class, userId);
	}

	public int cancel(UUID id, UUID userId, OffsetDateTime cancelledAt) {
		return jdbcTemplate.update(cancel, cancelledAt, cancelledAt, id, userId);
	}

	public record OrderPageRow(OrderEntity order, int itemCount) {}

	/**
	 * Finds all orders matching the specified status, ordered by creation date descending.
	 *
	 * @param status the order status to filter by
	 * @return a list of matching orders
	 */
	public List<OrderEntity> findByStatus(OrderStatus status) {
		return jdbcTemplate.query(findByStatus, orderMapper, status.getValue());
	}

	/**
	 * Finds all orders created within the specified date range, ordered by creation date descending.
	 *
	 * @param startDate the start of the date range (inclusive)
	 * @param endDate the end of the date range (inclusive)
	 * @return a list of matching orders
	 */
	public List<OrderEntity> findByDateRange(OffsetDateTime startDate, OffsetDateTime endDate) {
		return jdbcTemplate.query(findByDateRange, orderMapper, startDate, endDate);
	}

	private void bindFields(PreparedStatement statement, OrderEntity orderEntity, int startIndex) throws SQLException {
		statement.setObject(startIndex++, orderEntity.userId());
		statement.setObject(startIndex++, orderEntity.status().getValue(), java.sql.Types.OTHER);
		statement.setString(startIndex++, orderEntity.currency());
		statement.setBigDecimal(startIndex++, orderEntity.subtotalAmount());
		statement.setBigDecimal(startIndex++, orderEntity.taxAmount());
		statement.setBigDecimal(startIndex++, orderEntity.shippingAmount());
		statement.setBigDecimal(startIndex++, orderEntity.discountAmount());

		statement.setObject(startIndex++, toJsonb(orderEntity.shippingAddress()));
		statement.setObject(startIndex++, toJsonb(orderEntity.billingAddress()));

		statement.setString(startIndex++, orderEntity.customerNote());
		statement.setLong(startIndex++, orderEntity.version());

		statement.setObject(startIndex++, orderEntity.inventoryReservedAt());
		statement.setObject(startIndex++, orderEntity.paidAt());
		statement.setObject(startIndex++, orderEntity.confirmedAt());
		statement.setObject(startIndex++, orderEntity.shippedAt());
		statement.setObject(startIndex++, orderEntity.deliveredAt());
		statement.setObject(startIndex++, orderEntity.cancelledAt());
		statement.setObject(startIndex++, orderEntity.refundedAt());
		statement.setObject(startIndex++, orderEntity.createdAt());
		statement.setObject(startIndex, orderEntity.updatedAt());
	}

	private PGobject toJsonb(String json) throws SQLException {
		if (json == null) {
			return null;
		}
		PGobject pgObject = new PGobject();
		pgObject.setType("jsonb");
		pgObject.setValue(json);
		return pgObject;
	}

	private OrderEntity orderMapper(ResultSet resultSet, int rowNumber) throws SQLException {
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
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}
}
