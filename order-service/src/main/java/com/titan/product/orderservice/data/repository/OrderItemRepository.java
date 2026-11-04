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

import com.titan.product.orderservice.data.entity.OrderItemEntity;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderItemRepository {
	private static final String COLUMNS = """
        id, order_id, product_id, variant_id, product_name, variant_name, \
        sku, attributes, quantity, unit_price, position, created_at\
        """;

	private static final String tableName = "order_items";

	private static final String insertStatement = """
            INSERT INTO %s (
                id, order_id, product_id, variant_id, product_name, variant_name,
                sku, attributes, quantity, unit_price, position, created_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """.formatted(tableName);

	private static final String findById = """
            SELECT %s FROM %s WHERE id = ?
            """.formatted(COLUMNS, tableName);

	private static final String findByOrderId = """
            SELECT %s FROM %s WHERE order_id = ? ORDER BY position ASC
            """.formatted(COLUMNS, tableName);

	private final RowMapper<OrderItemEntity> orderItemMapper = this::orderItemMapper;
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
		jdbcTemplate.update(connection -> {
			PreparedStatement statement = connection.prepareStatement(insertStatement);
			statement.setObject(1, orderItemEntity.id());
			bindFields(statement, orderItemEntity, 2);
			return statement;
		});
	}

	/**
	 * Finds an order item by its unique identifier.
	 *
	 * @param id the unique order item ID
	 * @return an Optional containing the order item if found, or empty otherwise
	 */
	public Optional<OrderItemEntity> findById(UUID id) {
		List<OrderItemEntity> results = jdbcTemplate.query(findById, orderItemMapper, id);
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

	/**
	 * Finds all order items associated with a specific order, ordered by their display position.
	 *
	 * @param orderId the order ID to filter by
	 * @return a list of matching order items
	 */
	public List<OrderItemEntity> findByOrderId(UUID orderId) {
		return jdbcTemplate.query(findByOrderId, orderItemMapper, orderId);
	}

	private void bindFields(PreparedStatement statement, OrderItemEntity orderItemEntity, int startIndex) throws SQLException {
		statement.setObject(startIndex++, orderItemEntity.orderId());
		statement.setObject(startIndex++, orderItemEntity.productId());
		statement.setObject(startIndex++, orderItemEntity.variantId());
		statement.setString(startIndex++, orderItemEntity.productName());
		statement.setString(startIndex++, orderItemEntity.variantName());
		statement.setString(startIndex++, orderItemEntity.sku());
		statement.setObject(startIndex++, toJsonb(orderItemEntity.attributes()));
		statement.setInt(startIndex++, orderItemEntity.quantity());
		statement.setBigDecimal(startIndex++, orderItemEntity.unitPrice());
		statement.setShort(startIndex++, orderItemEntity.position());
		statement.setObject(startIndex, orderItemEntity.createdAt());
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

	private OrderItemEntity orderItemMapper(ResultSet resultSet, int rowNumber) throws SQLException {
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
				resultSet.getObject("created_at", OffsetDateTime.class)
		);
	}
}
