package com.tool.otsutil;

import com.tool.otsutil.config.CitiesConfig;
import com.tool.otsutil.model.common.measureType;
import com.tool.otsutil.service.OtsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

@SpringBootTest
@EnableConfigurationProperties(CitiesConfig.class)
class OtsUtilApplicationTests {

    @Autowired
    OtsService otsService;

    @Test
    void contextLoads() {
        boolean b = otsService.checkKeyExists("1239388418107582720241115000000");
        System.out.println(b);
    }

    @Test
    void getOtsValue() {
        BigDecimal previousValue = otsService.getOtsValue("12312312399999999999999");
        System.out.println(previousValue);
    }

    @Test
    void writeOtsValue() {
        otsService.writeData("12312312399999999999999", new BigDecimal("225.31415"), "2024-11-17 00:00:00");
    }

    @Test
    void getMeasure() {
        measureType measureType = OtsService.getMeasureType("12393884046852459");
        System.out.println(measureType.getMeasure());
    }

}
