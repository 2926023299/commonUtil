package com.tool.otsutil;

import com.tool.otsutil.config.CitiesConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.tool.otsutil.mapper")
public class CommonUtilApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommonUtilApplication.class, args);
    }

}
