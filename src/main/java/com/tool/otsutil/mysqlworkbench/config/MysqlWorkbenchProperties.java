package com.tool.otsutil.mysqlworkbench.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mysql-workbench")
public class MysqlWorkbenchProperties {

    private Query query = new Query();

    private Export export = new Export();

    @Data
    public static class Query {

        private int displayLimitDefault = 1000;

        private int displayLimitMax = 5000;

        private int tablePageSizeMax = 200;
    }

    @Data
    public static class Export {

        private String dir = "mysql-workbench-exports";

        private int threadPoolSize = 2;

        private int retentionHours = 24;
    }
}
