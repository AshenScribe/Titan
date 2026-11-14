package com.titan.payment.infrastructure.persistence.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

public final class JdbcSupport {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private JdbcSupport() {}

    public static PGobject toPgObject(Map<String, Object> value) {
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(value == null ? "{}" : MAPPER.writeValueAsString(value));
            return pg;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialize jsonb", e);
        }
    }

    public static void bind(PreparedStatement statement, int index, Map<String, Object> value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
            return;
        }
        statement.setObject(index, toPgObject(value));
    }

    public static Map<String, Object> fromPgObject(Object pgObj) {
        if (pgObj == null) return Map.of();
        try {
            String s = (pgObj instanceof PGobject p) ? p.getValue() : pgObj.toString();
            return (s == null || s.isBlank()) ? Map.of() : MAPPER.readValue(s, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot deserialize jsonb", e);
        }
    }

    public static Map<String, Object> readJsonb(ResultSet rs, String column) throws SQLException {
        return fromPgObject(rs.getObject(column));
    }

    public static PGobject asParam(Map<String, Object> value) {
        return toPgObject(value);
    }

    public static UUID readUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        throw new IllegalStateException(
                "Column '" + column + "' is not a UUID: " + value.getClass().getName());
    }

    public static OffsetDateTime readOffsetDateTime(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) return null;
        if (value instanceof OffsetDateTime odt) return odt;
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        throw new IllegalStateException(
                "Column '" + column + "' is not a timestamp: " + value.getClass().getName());
    }
}
