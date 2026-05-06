package com.kdm.migration.config;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class MigrationSqlBuilder {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private MigrationSqlBuilder() {
    }

    static String insertSql(String schema, String tableName, Collection<String> columns) {
        validateIdentifier(schema, "schema");
        validateIdentifier(tableName, "table");
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        columns.forEach(column -> validateIdentifier(column, "column"));

        String columnList = String.join(", ", columns);
        String parameters = columns.stream()
                .map(column -> ":" + column)
                .collect(Collectors.joining(", "));

        return "INSERT INTO " + schema + "." + tableName
                + " (" + columnList + ") VALUES (" + parameters + ")";
    }

    private static void validateIdentifier(String identifier, String label) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Unsafe " + label + " identifier: " + identifier);
        }
    }
}
