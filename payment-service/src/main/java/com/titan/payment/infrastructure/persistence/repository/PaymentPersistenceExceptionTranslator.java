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

package com.titan.payment.infrastructure.persistence.repository;

import com.titan.payment.infrastructure.persistence.exception.*;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public final class PaymentPersistenceExceptionTranslator {

    private static final String CHECK_VIOLATION = "23514";
    private static final String NOT_NULL_VIOLATION = "23502";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String INVALID_TEXT_REPRESENTATION = "22P02";
    private static final String STRING_DATA_RIGHT_TRUNCATION = "22001";

    private PaymentPersistenceExceptionTranslator() {}

    public static PaymentDataException translate( DataAccessException exception) {
        PSQLException pg = findPostgresException(exception);
        if (pg == null) {
            return new PaymentDataException("Unexpected database access error.", exception);
        }
        String sqlState = pg.getSQLState();
        if (sqlState == null) {
            return new PaymentDataException("Database operation failed (sqlState=null)", exception);
        }
        String constraintName = getConstraintName(pg);
        return switch (sqlState) {
            case CHECK_VIOLATION -> new PaymentCheckConstraintViolationException(pg);
            case NOT_NULL_VIOLATION -> new PaymentNotNullViolationException(pg);
            case FOREIGN_KEY_VIOLATION -> new PaymentForeignKeyViolationException(pg);
            case UNIQUE_VIOLATION -> translateUniqueViolation(pg, constraintName);
            case INVALID_TEXT_REPRESENTATION -> new PaymentInvalidEnumValueException(pg);
            case STRING_DATA_RIGHT_TRUNCATION -> new PaymentStringTruncationException(pg);
            default -> new PaymentDataException(
                    "Unhandled database error (sqlState=" + sqlState + ")", pg);
        };
    }

    private static PaymentDataException translateUniqueViolation(PSQLException e, String constraint) {
        if (constraint == null) return new PaymentDataException("Unique constraint violation.", e);
        return switch (constraint) {
            case "payment_attempts_pkey" -> new DuplicatePaymentAttemptException(e);
            case "uq_payment_attempt_idempotency" ->
                    new DuplicatePaymentAttemptException(
                            "Attempt already exists for this order + idempotency key.", e);
            case "gateway_payments_pkey" -> new DuplicateGatewayPaymentException(e);
            case "gateway_payments_gateway_payment_id_key" ->
                    new DuplicateGatewayPaymentException(
                            "Gateway payment id already recorded.", e);
            case "refunds_pkey" -> new DuplicateRefundException(e);
            case "refunds_gateway_refund_id_key" ->
                    new DuplicateRefundException("Gateway refund id already recorded.", e);
            case "webhook_inbox_pkey" -> new DuplicateWebhookEventException(e);
            case "webhook_inbox_gateway_event_id_key" ->
                    new DuplicateWebhookEventException("Webhook event already received.", e);
            case "payment_attempt_events_pkey" -> new DuplicatePaymentAttemptEventException(e);
            default -> new PaymentDataException("Unique constraint violation: " + constraint, e);
        };
    }

    private static PSQLException findPostgresException(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof PSQLException pg) return pg;
            if (cur instanceof SQLException sq && sq.getNextException() != null) {
                PSQLException next = findPostgresException(sq.getNextException());
                if (next != null) return next;
            }
            cur = cur.getCause();
        }
        return null;
    }

    private static String getConstraintName(PSQLException e) {
        ServerErrorMessage msg = e.getServerErrorMessage();
        return msg == null ? null : msg.getConstraint();
    }
}
