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

import java.sql.SQLException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public final class OrderPersistenceExceptionTranslator {

    private static final String CHECK_VIOLATION = "23514";
    private static final String NOT_NULL_VIOLATION = "23502";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String INVALID_TEXT_REPRESENTATION = "22P02";
    private static final String STRING_DATA_RIGHT_TRUNCATION = "22001";

    private OrderPersistenceExceptionTranslator() {}

    /**
     * Translates a Spring data-access exception into an application persistence exception.
     *
     * @param exception the original data-access exception
     * @return the translated persistence exception
     */
    public static OrderDataException translate(DataAccessException exception) {
        PSQLException postgresException = findPostgresException(exception);

        if (postgresException == null) {
            return new OrderDataException("Unexpected database access error.", exception);
        }

        String sqlState = postgresException.getSQLState();
        String constraintName = getConstraintName(postgresException);
        if (sqlState == null) {
            return new OrderDataException("Database operation failed (sqlState=null)", exception);
        }
        return switch (sqlState) {
            case CHECK_VIOLATION -> new OrderCheckConstraintViolationException(postgresException);

            case NOT_NULL_VIOLATION -> new OrderNotNullViolationException(postgresException);

            case FOREIGN_KEY_VIOLATION -> new OrderForeignKeyViolationException(postgresException);

            case UNIQUE_VIOLATION -> translateUniqueViolation(postgresException, constraintName);

            case INVALID_TEXT_REPRESENTATION ->
                    new OrderInvalidEnumValueException(postgresException);

            case STRING_DATA_RIGHT_TRUNCATION ->
                    new OrderStringTruncationException(postgresException);

            default ->
                    new OrderDataException(
                            "Unhandled database error (sqlState=" + sqlState + ")",
                            postgresException);
        };
    }

    private static OrderDataException translateUniqueViolation(
            PSQLException exception, String constraintName) {

        if (constraintName == null) {
            return new OrderDataException("Unique constraint violation.", exception);
        }

        return switch (constraintName) {
            case "orders_pkey" -> new DuplicateOrderException(exception);
            case "order_idempotency_order_id_key" ->
                    new DuplicateOrderException(
                            "Order is already referenced by an idempotency record.", exception);
            case "order_items_pkey", "uq_order_item_position", "uq_order_item_variant" ->
                    new DuplicateOrderItemException(exception);
            default ->
                    new OrderDataException(
                            "Unique constraint violation: " + constraintName, exception);
        };
    }

    private static PSQLException findPostgresException(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof PSQLException postgresException) {
                return postgresException;
            }

            if (current instanceof SQLException sqlException
                    && sqlException.getNextException() != null) {
                PSQLException next = findPostgresException(sqlException.getNextException());

                if (next != null) {
                    return next;
                }
            }

            current = current.getCause();
        }

        return null;
    }

    private static String getConstraintName(PSQLException exception) {
        ServerErrorMessage serverError = exception.getServerErrorMessage();

        return serverError == null ? null : serverError.getConstraint();
    }
}
