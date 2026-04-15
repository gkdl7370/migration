package com.kdm.migration.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.JdbcTransactionManager;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    @Bean @ConfigurationProperties("spring.datasource.postgres")
    public DataSourceProperties postgresDataSourceProperties() { return new DataSourceProperties(); }

    @Bean @Primary
    public DataSource postgresDataSource() {
        return postgresDataSourceProperties().initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    @Bean @Primary
    public JdbcTransactionManager postgresTransactionManager(DataSource postgresDataSource) {
        return new JdbcTransactionManager(postgresDataSource);
    }

    @Bean @ConfigurationProperties("spring.datasource.oracle")
    public DataSourceProperties oracleDataSourceProperties() { return new DataSourceProperties(); }

    @Bean
    public DataSource oracleDataSource() {
        return oracleDataSourceProperties().initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}