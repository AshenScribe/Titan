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
package com.titan.product.orderservice.data.exception;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.UncategorizedDataAccessException;

class OrderPersistenceExceptionTranslatorTest {

    @ParameterizedTest
    @CsvSource({
        "23514, OrderCheckConstraintViolationException",
        "23502, OrderNotNullViolationException",
        "23503, OrderForeignKeyViolationException",
        "22P02, OrderInvalidEnumValueException",
        "22001, OrderStringTruncationException"
    })
    void mapsSqlStateToExpectedExceptionType(String sqlState, String expectedSimpleName) {
        DataAccessException input = wrap(psql(sqlState, null));

        OrderDataException result = OrderPersistenceExceptionTranslator.translate(input);

        Assertions.assertThat(result)
                .hasMessageContaining("")
                .isInstanceOf(OrderDataException.class);
        Assertions.assertThat(result.getClass().getSimpleName()).isEqualTo(expectedSimpleName);
    }

    @Test
    void mapsOrdersPrimaryKeyViolationToDuplicateOrder() {
        OrderDataException result = translateUnique("orders_pkey");

        Assertions.assertThat(result)
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessage("Duplicate order");
    }

    @Test
    void mapsIdempotencyOrderKeyViolationToDuplicateOrderWithSpecificMessage() {
        OrderDataException result = translateUnique("order_idempotency_order_id_key");

        Assertions.assertThat(result)
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessage("Order is already referenced by an idempotency record.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"order_items_pkey", "uq_order_item_position", "uq_order_item_variant"})
    void mapsOrderItemUniqueViolationsToDuplicateOrderItem(String constraint) {
        OrderDataException result = translateUnique(constraint);

        Assertions.assertThat(result)
                .isInstanceOf(DuplicateOrderItemException.class)
                .hasMessage("Duplicate order item");
    }

    @Test
    void mapsUnknownUniqueConstraintToGenericOrderDataException() {
        OrderDataException result = translateUnique("some_other_unique_constraint");

        Assertions.assertThat(result)
                .isInstanceOf(OrderDataException.class)
                .isNotInstanceOf(DuplicateOrderException.class)
                .isNotInstanceOf(DuplicateOrderItemException.class)
                .hasMessageContaining("some_other_unique_constraint");
    }

    @Test
    void mapsUniqueViolationWithoutConstraintNameToGenericMessage() {
        PSQLException psql = psql("23505", null);
        DataAccessException input = new DuplicateKeyException("dup", psql);

        OrderDataException result = OrderPersistenceExceptionTranslator.translate(input);

        Assertions.assertThat(result)
                .isInstanceOf(OrderDataException.class)
                .isNotInstanceOf(DuplicateOrderException.class)
                .isNotInstanceOf(DuplicateOrderItemException.class)
                .hasMessage("Unique constraint violation.");
    }

    @Test
    void unknownSqlStateProducesGenericExceptionWithCodeInMessage() {
        PSQLException psql = psql("99999", null);

        OrderDataException result =
                OrderPersistenceExceptionTranslator.translate(
                        new DataIntegrityViolationException("x", psql));

        Assertions.assertThat(result)
                .isInstanceOf(OrderDataException.class)
                .isNotInstanceOf(OrderCheckConstraintViolationException.class)
                .hasMessageContaining("99999");
    }

    @Test
    void missingPostgresExceptionProducesGenericUnexpectedError() {
        DataAccessException input = new DataIntegrityViolationException("no psql here");

        OrderDataException result = OrderPersistenceExceptionTranslator.translate(input);

        Assertions.assertThat(result)
                .isInstanceOf(OrderDataException.class)
                .hasMessage("Unexpected database access error.")
                .hasCause(input);
    }

    @Test
    void sqlStateNullProducesGenericExceptionWithNullInMessage() {
        PSQLException psql = psql(null, null);
        DataAccessException input = new DataIntegrityViolationException("x", psql);

        OrderDataException result = OrderPersistenceExceptionTranslator.translate(input);

        Assertions.assertThat(result)
                .isInstanceOf(OrderDataException.class)
                .hasMessageContaining("sqlState=null");
    }

    @Test
    void findsPostgresExceptionDeepInCauseChain() {
        PSQLException psql = psql("23514", null);
        DataAccessException input =
                new UncategorizedDataAccessException(
                        "wrap", new RuntimeException("middle", psql)) {};

        OrderDataException result = OrderPersistenceExceptionTranslator.translate(input);

        Assertions.assertThat(result).isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    @Test
    void findsPostgresExceptionViaNextException() {
        PSQLException psql = psql("23514", null);
        SQLException wrapper = new SQLException("outer");
        wrapper.setNextException(psql);
        DataAccessException input = new UncategorizedDataAccessException("wrap", wrapper) {};

        OrderDataException result = OrderPersistenceExceptionTranslator.translate(input);

        Assertions.assertThat(result).isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    @Test
    void prefersCauseChainPsqlOverNextException() {
        PSQLException causePath = psql("23514", null);
        PSQLException nextPath = psql("23502", null);
        SQLException sqlWithNext = new SQLException("outer");
        sqlWithNext.setNextException(nextPath);
        DataAccessException input =
                new UncategorizedDataAccessException(
                        "wrap", new RuntimeException("middle", causePath)) {};

        OrderDataException result = OrderPersistenceExceptionTranslator.translate(input);

        Assertions.assertThat(result).isInstanceOf(OrderCheckConstraintViolationException.class);
    }

    @Test
    void uniqueViolationWithNullConstraintNameFallsBackToGenericMessage() {
        PSQLException psql = psql("23505", null);
        DataAccessException input = new DuplicateKeyException("dup", psql);

        OrderDataException result = OrderPersistenceExceptionTranslator.translate(input);

        Assertions.assertThat(result)
                .isNotInstanceOf(DuplicateOrderException.class)
                .isNotInstanceOf(DuplicateOrderItemException.class)
                .hasMessage("Unique constraint violation.");
    }

    private static OrderDataException translateUnique(String constraint) {
        return OrderPersistenceExceptionTranslator.translate(
                new DuplicateKeyException("dup", psql("23505", constraint)));
    }

    private static DataAccessException wrap(PSQLException psql) {
        return new DataIntegrityViolationException("wrapped", psql);
    }

    private static PSQLException psql(String sqlState, String constraint) {
        PSQLException psql = mock(PSQLException.class);
        when(psql.getSQLState()).thenReturn(sqlState);
        if (constraint != null) {
            ServerErrorMessage serverError = mock(ServerErrorMessage.class);
            when(serverError.getConstraint()).thenReturn(constraint);
            when(psql.getServerErrorMessage()).thenReturn(serverError);
        } else {
            when(psql.getServerErrorMessage()).thenReturn(null);
        }
        return psql;
    }
}
