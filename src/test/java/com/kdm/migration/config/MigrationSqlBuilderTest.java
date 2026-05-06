package com.kdm.migration.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationSqlBuilderTest {

    @Test
    @DisplayName("컬럼 목록으로 named parameter INSERT SQL을 만든다")
    void insertSqlBuildsNamedParameterStatement() {
        String sql = MigrationSqlBuilder.insertSql(
                "KDM",
                "WATER_LEVEL",
                List.of("OBS_ID", "MEASURED_AT", "VALUE")
        );

        assertThat(sql).isEqualTo(
                "INSERT INTO KDM.WATER_LEVEL (OBS_ID, MEASURED_AT, VALUE) " +
                        "VALUES (:OBS_ID, :MEASURED_AT, :VALUE)"
        );
    }

    @Test
    @DisplayName("위험한 테이블명은 거부한다")
    void insertSqlRejectsUnsafeTableName() {
        assertThatThrownBy(() -> MigrationSqlBuilder.insertSql(
                "KDM",
                "WATER_LEVEL;DROP_TABLE",
                List.of("OBS_ID")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe table identifier");
    }

    @Test
    @DisplayName("컬럼 목록이 비어 있으면 SQL을 만들지 않는다")
    void insertSqlRejectsEmptyColumns() {
        assertThatThrownBy(() -> MigrationSqlBuilder.insertSql("KDM", "WATER_LEVEL", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columns must not be empty");
    }
}
