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
        
        // 检查breaker_energy_data表是否存在
        if (!tableExists("breaker_energy_data")) {
            log.info("breaker_energy_data表不存在，开始创建...");
            createBreakerEnergyDataTable();
            log.info("breaker_energy_data表创建完成");
        } else {
            log.info("breaker_energy_data表已存在，跳过创建");
        }
    }

    /**
     * 检查表是否存在
     */
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

    /**
     * 创建breaker_energy_data表
     */
    private void createBreakerEnergyDataTable() {
        try {
            // 读取SQL脚本
            ClassPathResource resource = new ClassPathResource("sql/init.sql");
            String sqlScript = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            
            // 执行SQL
            jdbcTemplate.execute(sqlScript);
            log.info("成功执行SQL脚本创建breaker_energy_data表");
        } catch (Exception e) {
            log.error("创建breaker_energy_data表失败", e);
            throw new RuntimeException("创建breaker_energy_data表失败", e);
        }
    }
}