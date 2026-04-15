package com.kdm.migration.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MigrationBatchConfig {

    private static final int CHUNK_SIZE = 1000;
    private static final int CONCURRENCY_LIMIT = 5; // 동시에 이관할 테이블 개수

    @Bean
    public Job masterMigrationJob(JobRepository jobRepository,
                                  PlatformTransactionManager txManager,
                                  @Qualifier("oracleDataSource") DataSource oracleDs,
                                  @Qualifier("postgresDataSource") DataSource postgresDs) throws Exception {

        // 1. Oracle 메타데이터에서 테이블 목록 자동 추출 (운영 자동화)
        List<String> tableNames = fetchTableNames(oracleDs);
        log.info(">>>> [자동 탐색 완료] 총 {}개의 테이블을 이관 대상으로 선정했습니다.", tableNames.size());

        // 2. 각 테이블별 Step 리스트 생성
        List<Flow> flows = new ArrayList<>();
        for (String tableName : tableNames) {
            Step step = createStep(tableName, jobRepository, txManager, oracleDs, postgresDs);
            flows.add(new FlowBuilder<SimpleFlow>(tableName + "_Flow").start(step).build());
        }

        // 3. 병렬 처리 설정 (High-Concurrency)
        // TaskExecutor를 사용하여 지정된 숫자만큼 테이블을 동시에 이관
        return new JobBuilder("smartMigrationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(buildParallelFlow(flows))
                .end()
                .build();
    }

    // 병렬 실행기 (Flow들을 병렬로 묶음)
    private Flow buildParallelFlow(List<Flow> flows) {
        return new FlowBuilder<SimpleFlow>("parallelFlow")
                .split(taskExecutor())
                .add(flows.toArray(new Flow[0]))
                .build();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setConcurrencyLimit(CONCURRENCY_LIMIT); // 시스템 자원 보호를 위한 동시성 제어
        return executor;
    }

    private List<String> fetchTableNames(DataSource ds) throws Exception {
        List<String> tables = new ArrayList<>();
        try (var conn = ds.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            // KDM 스키마의 모든 TABLE 타입 조회
            ResultSet rs = metaData.getTables(null, "KDM", "%", new String[]{"TABLE"});
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                // 시스템 테이블 및 불필요한 테이블 필터링 로직 추가 가능
                if (!name.startsWith("BIN$")) {
                    tables.add(name);
                }
            }
        }
        return tables;
    }

    private Step createStep(String tableName, JobRepository jobRepo, PlatformTransactionManager tx,
                            DataSource oracleDs, DataSource postgresDs) {
        return new StepBuilder(tableName + "_Step", jobRepo)
                .<Map<String, Object>, Map<String, Object>>chunk(CHUNK_SIZE, tx)
                .reader(dynamicCursorReader(tableName, oracleDs))
                .writer(dynamicPlainWriter(tableName, postgresDs))
                .allowStartIfComplete(true)
                .build();
    }

    private JdbcCursorItemReader<Map<String, Object>> dynamicCursorReader(String tableName, DataSource ds) {
        return new JdbcCursorItemReaderBuilder<Map<String, Object>>()
                .name(tableName + "_Reader")
                .dataSource(ds)
                .sql("SELECT * FROM KDM." + tableName)
                .rowMapper(new ColumnMapRowMapper())
                .fetchSize(CHUNK_SIZE)
                .build();
    }

    private ItemWriter<Map<String, Object>> dynamicPlainWriter(String tableName, DataSource ds) {
        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(ds);
        return chunk -> {
            if (chunk.isEmpty()) return;
            Set<String> cols = chunk.getItems().get(0).keySet();
            String sql = String.format("INSERT INTO KDM.%s (%s) VALUES (%s)",
                    tableName,
                    String.join(", ", cols),
                    cols.stream().map(c -> ":" + c).collect(Collectors.joining(", ")));
            template.batchUpdate(sql, chunk.getItems().toArray(new Map[0]));
        };
    }
}