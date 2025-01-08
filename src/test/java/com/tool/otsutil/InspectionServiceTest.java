package com.tool.otsutil;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.tool.otsutil.config.CitiesConfig;
import com.tool.otsutil.service.InspectionService;
import com.tool.otsutil.service.TuMoStatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@SpringBootTest
@EnableConfigurationProperties(CitiesConfig.class)
public class InspectionServiceTest {

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private TuMoStatisticsService tuMoStatisticsService;

    @Test
    public void testPerformInspection() throws Exception {
        // 获取当前日期
        DateTime currentDate = DateUtil.date();

        // 获取上周一的日期
        DateTime lastMonday = DateUtil.offsetDay(DateUtil.beginOfWeek(currentDate, true), -7);

        // 获取上周日的日期
        DateTime lastSunday = DateUtil.offsetDay(DateUtil.endOfWeek(currentDate, true), -7);

        // 输出结果
        System.out.println("上周一: " + DateUtil.format(lastMonday, "yyyy-MM-dd"));
        System.out.println("上周日: " + DateUtil.format(lastSunday, "yyyy-MM-dd"));
    }

}
