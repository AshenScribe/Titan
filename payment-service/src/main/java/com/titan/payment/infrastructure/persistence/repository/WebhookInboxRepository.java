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

import com.titan.payment.domain.port.WebhookInboxPort;
import com.titan.payment.infrastructure.persistence.model.WebhookInboxEntity;
import com.titan.payment.infrastructure.persistence.util.JdbcSupport;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
public class WebhookInboxRepository implements WebhookInboxPort {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String COLUMNS =
            "id, gateway_event_id, event_type, payload, occurred_at, received_at, "
                    + "processed_at, status, processing_attempts, last_error";

    private static final String TABLE_NAME = "webhook_inbox";

    /**
     * Idempotent insert; returns 1 if newly inserted, 0 if gateway_event_id already existed.
     */
    private static final String INSERT_IF_ABSENT =
            """
                    INSERT INTO webhook_inbox (
                        id, gateway_event_id, event_type, payload, occurred_at,
                        received_at, processed_at, status, processing_attempts, last_error
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (gateway_event_id) DO NOTHING
                    RETURNING id
                    """.formatted(TABLE_NAME);

    private static final String FIND_BY_ID =
            "SELECT %s FROM %s WHERE id = ?".formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_BY_GATEWAY_EVENT_ID =
            "SELECT %s FROM %s WHERE gateway_event_id = ?".formatted(COLUMNS, TABLE_NAME);

    private static final String FIND_UNPROCESSED =
            """
                    SELECT %s FROM %s
                    WHERE status IN ('RECEIVED', 'FAILED')
                    ORDER BY received_at
                    LIMIT ?
                    """.formatted(COLUMNS, TABLE_NAME);

    /**
     * Atomic claim: only one worker moves RECEIVED/FAILED → PROCESSING and bumps attempts.
     */
    private static final String CLAIM_FOR_PROCESSING =
            """
                    UPDATE %s
                    SET status              = 'PROCESSING',
                        processing_attempts = processing_attempts + 1
                    WHERE id = ?
                      AND status IN ('RECEIVED', 'FAILED')
                      AND processing_attempts < ?
                    RETURNING %s
                    """.formatted(TABLE_NAME, COLUMNS);

    private static final String MARK_PROCESSED =
            """
                    UPDATE %s
                    SET status = 'PROCESSED', processed_at = ?
                    WHERE id = ? AND status = 'PROCESSING'
                    """.formatted(TABLE_NAME);

    private static final String MARK_FAILED =
            """
                    UPDATE %s
                    SET status = 'FAILED', processed_at = ?, last_error = ?
                    WHERE id = ? AND status = 'PROCESSING'
                    """.formatted(TABLE_NAME);

    private final RowMapper<WebhookInboxEntity> rowMapper = this::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public WebhookInboxRepository( JdbcTemplate jdbcTemplate ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> insertIfAbsent( WebhookInboxEntity entity ) {
        return execute(() ->
                               jdbcTemplate.query(
                                                   INSERT_IF_ABSENT,
                                                   statement -> bindInsert(statement, entity),
                                                   ( rs, rowNum ) -> rs.getObject("id", UUID.class)
                                           )
                                           .stream()
                                           .findFirst()
        );
    }

    public Optional<WebhookInboxEntity> findById( UUID id ) {
        return execute(() -> jdbcTemplate.query(FIND_BY_ID, rowMapper, id)
                                         .stream()
                                         .findFirst());
    }

    public Optional<WebhookInboxEntity> findByGatewayEventId( String gatewayEventId ) {
        Objects.requireNonNull(gatewayEventId, "gatewayEventId must not be null");
        return execute(() ->
                               jdbcTemplate.query(FIND_BY_GATEWAY_EVENT_ID, rowMapper, gatewayEventId)
                                           .stream()
                                           .findFirst());
    }

    public List<WebhookInboxEntity> findUnprocessed( int limit ) {
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        return execute(() -> jdbcTemplate.query(FIND_UNPROCESSED, rowMapper, limit));
    }

    public Optional<WebhookInboxEntity> claimForProcessing( UUID id, int maxAttempts ) {
        return execute(() ->
                               jdbcTemplate.query(CLAIM_FOR_PROCESSING, rowMapper, id, maxAttempts)
                                           .stream()
                                           .findFirst());
    }

    public int markProcessed( UUID id, OffsetDateTime processedAt ) {
        return execute(() -> jdbcTemplate.update(MARK_PROCESSED, processedAt, id));
    }

    public int markFailed( UUID id, OffsetDateTime processedAt, String lastError ) {
        return execute(() -> jdbcTemplate.update(MARK_FAILED, processedAt, lastError, id));
    }

    private void bindInsert( PreparedStatement statement, WebhookInboxEntity entity )
            throws SQLException {
        int index = 1;
        statement.setObject(index++, entity.id());
        statement.setString(index++, entity.gatewayEventId());
        statement.setString(index++, entity.eventType());
        JdbcSupport.bind(statement, index++, entity.payload());
        if (entity.occurredAt() != null) {
            statement.setObject(index++, entity.occurredAt());
        } else {
            statement.setNull(index++, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        statement.setObject(index++, entity.receivedAt());
        statement.setObject(index++, entity.processedAt());
        statement.setString(index++, entity.status());
        statement.setInt(index++, entity.processingAttempts());
        statement.setString(index, entity.lastError());
    }

    private WebhookInboxEntity mapRow( java.sql.ResultSet rs, int rowNumber ) throws SQLException {
        return new WebhookInboxEntity(
                JdbcSupport.readUuid(rs, "id"),
                rs.getString("gateway_event_id"),
                rs.getString("event_type"),
                JdbcSupport.readJsonb(rs, "payload"),
                JdbcSupport.readOffsetDateTime(rs, "occurred_at"),
                JdbcSupport.readOffsetDateTime(rs, "received_at"),
                JdbcSupport.readOffsetDateTime(rs, "processed_at"),
                rs.getString("status"),
                rs.getInt("processing_attempts"),
                rs.getString("last_error"));
    }

    private <T> T execute( Supplier<T> operation ) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            throw PaymentPersistenceExceptionTranslator.translate(exception);
        }
    }
}