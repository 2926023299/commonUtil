package com.tool.otsutil.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.stream.Collectors;

/**
 * 数据库初始化组件
 * 在应用启动时检查并创建必要的表
 */
@Slf4j
@Component
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始检查数据库表结构...");

        ensureTable("breaker_energy_data", this::createBreakerEnergyDataTable);
        ensureTable("mysql_query_history", this::createMysqlQueryHistoryTable);
        ensureTable("mysql_query_history_statement", this::createMysqlQueryHistoryStatementTable);
    }

    private void ensureTable(String tableName, Runnable creator) {
        if (!tableExists(tableName)) {
            log.info("{} 表不存在，开始创建...", tableName);
            creator.run();
            log.info("{} 表创建完成", tableName);
            return;
        }
        log.info("{} 表已存在，跳过创建", tableName);
    }

    private boolean tableExists(String tableName) {
        try {
            Connection connection = jdbcTemplate.getDataSource().getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, tableName, null);
            boolean exists = tables.next();
            tables.close();
            connection.close();
            return exists;
        } catch (Exception e) {
            log.error("检查表是否存在时出错: {}", tableName, e);
            return false;
        }
    }

    private void createBreakerEnergyDataTable() {
        try {
            ClassPathResource resource = new ClassPathResource("sql/init.sql");
            String sqlScript = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            jdbcTemplate.execute(sqlScript);
            log.info("成功执行SQL脚本创建breaker_energy_data表");
        } catch (Exception e) {
            log.error("创建breaker_energy_data表失败", e);
            throw new RuntimeException("创建breaker_energy_data表失败", e);
        }
    }

    private void createMysqlQueryHistoryTable() {
        jdbcTemplate.execute("CREATE TABLE `mysql_query_history` ("
                + "`id` bigint(20) NOT NULL AUTO_INCREMENT,"
                + "`schema_name` varchar(128) DEFAULT NULL,"
                + "`batch_text` longtext NOT NULL,"
                + "`statement_count` int(11) NOT NULL DEFAULT 0,"
                + "`dangerous` tinyint(1) NOT NULL DEFAULT 0,"
                + "`executed_by` varchar(64) DEFAULT NULL,"
                + "`status` varchar(32) NOT NULL DEFAULT 'RUNNING',"
                + "`started_at` datetime NOT NULL,"
                + "`finished_at` datetime DEFAULT NULL,"
                + "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (`id`),"
                + "KEY `idx_mysql_query_history_started_at` (`started_at`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MySQL 工作台 SQL 执行历史批次表'");
    }

    private void createMysqlQueryHistoryStatementTable() {
        jdbcTemplate.execute("CREATE TABLE `mysql_query_history_statement` ("
                + "`id` bigint(20) NOT NULL AUTO_INCREMENT,"
                + "`history_id` bigint(20) NOT NULL,"
                + "`statement_order` int(11) NOT NULL,"
                + "`statement_text` longtext NOT NULL,"
                + "`statement_type` varchar(32) DEFAULT NULL,"
                + "`success` tinyint(1) NOT NULL DEFAULT 0,"
                + "`affected_rows` bigint(20) NOT NULL DEFAULT 0,"
                + "`result_size` int(11) NOT NULL DEFAULT 0,"
                + "`duration_ms` bigint(20) NOT NULL DEFAULT 0,"
                + "`error_message` text DEFAULT NULL,"
                + "`created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "PRIMARY KEY (`id`),"
                + "KEY `idx_mysql_query_statement_history` (`history_id`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MySQL 工作台 SQL 执行历史语句表'");
    }
}
