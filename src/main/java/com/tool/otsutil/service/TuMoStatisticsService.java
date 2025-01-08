package com.tool.otsutil.service;

import com.tool.otsutil.config.CitiesConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TuMoStatisticsService {

    @Value("${tumo.paths.zytumo}")
    private String zyPath;

    @Value("${tumo.paths.dytumo}")
    private String dyPath;

    @Autowired
    private CitiesConfig citiesConfig;

    @Value("${excel.export-path}")
    private String outputPath;

    /**
     * 统计中压文件数量
     *
     * @param date 日期字符串
     * @return 中压统计结果
     */


    public Map<String, Integer> getZyStatistics(String date) {
        String fullPath = zyPath.replace("{date}", date);
        return processDirectory(fullPath);
    }

    /**
     * 统计低压文件数量
     *
     * @param date 日期字符串
     * @return 低压统计结果
     */
    public Map<String, Integer> getDyStatistics(String date) {
        String fullPath = dyPath.replace("{date}", date);
        return processDirectory(fullPath);
    }

    /**
     * 导出统计数据到 Excel 文件，覆盖写入或追加 Sheet 表
     *
     * @param zyData 中压统计数据
     * @param dyData 低压统计数据
     */
    public void exportStatisticsToExcel(String fileName, Map<String, Integer> zyData, Map<String, Integer> dyData) {

        //我需要获取到当前日期，并格式化为 yyyy/MM/dd
        LocalDate date = LocalDate.now();

        String filePath = outputPath + fileName;
        Workbook workbook;

        // 检查文件是否存在
        File file = new File(filePath);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                workbook = new XSSFWorkbook(fis); // 加载已有文件
            } catch (IOException e) {
                throw new RuntimeException("加载 Excel 文件失败: " + filePath, e);
            }
        } else {
            workbook = new XSSFWorkbook(); // 创建新文件
        }

        // 检查 Sheet 是否存在
        Sheet sheet = workbook.getSheet("图模");
        if (sheet == null) {
            sheet = workbook.createSheet("图模"); // 创建新 Sheet
        } else {
            // 清除旧数据
            for (int i = sheet.getLastRowNum(); i >= 0; i--) {
                sheet.removeRow(sheet.getRow(i));
            }
        }

        //设置风格
        CellStyle cellStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Microsoft YaHei"); // 设置字体名称
        font.setFontHeightInPoints((short) 10); // 设置字体大小
        cellStyle.setFont(font);
        cellStyle.setBorderTop(BorderStyle.THIN); // 设置上边框
        cellStyle.setBorderBottom(BorderStyle.THIN); // 设置下边框
        cellStyle.setBorderLeft(BorderStyle.THIN); // 设置左边框
        cellStyle.setBorderRight(BorderStyle.THIN); // 设置右边框
        cellStyle.setAlignment(HorizontalAlignment.CENTER); // 设置水平居中
        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER); // 设置垂直居中

        // 写入表头
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue(date + "-图模");
        headerRow.createCell(1).setCellValue("中压（前一天）");
        headerRow.createCell(2).setCellValue("低压（前一天）");

        // 写入数据
        int rowIndex = 1;

        Map<String, String> cityMap = this.getCityMap();

        for (String cityCode : cityMap.keySet()) {
            Row dataRow = sheet.createRow(rowIndex++);
            dataRow.createCell(0).setCellValue(cityCode + cityMap.get(cityCode)); // 城市代码+名称
            dataRow.createCell(1).setCellValue(zyData.getOrDefault(cityCode, 0)); // 中压数据
            dataRow.createCell(2).setCellValue(dyData.getOrDefault(cityCode, 0)); // 低压数据
        }

        //设置单元格宽度22
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                Row row = sheet.getRow(i);
                Cell cell = row.getCell(j);
                cell.setCellStyle(cellStyle);
                row.setHeight((short) 400);
                sheet.setColumnWidth(j, 22 * 256);
            }
        }

        // 保存文件
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        } catch (IOException e) {
            throw new RuntimeException("写入 Excel 文件失败: " + filePath, e);
        }
    }

    /**
     * 处理文件夹，统计文件数量
     *
     * @param basePath 文件夹路径
     * @return 统计结果
     */
    private Map<String, Integer> processDirectory(String basePath) {
        Map<String, Integer> statistics = new HashMap<>();

        Map<String, String> cityMap = this.getCityMap();

        for (String cityCode : cityMap.keySet()) {
            String cityPath = basePath + cityCode;
            File directory = new File(cityPath);

            log.info("正在处理文件夹：{}", cityPath);

            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles();
                statistics.put(cityCode, files == null ? 0 : files.length);
            } else {
                statistics.put(cityCode, 0);
            }
        }

        log.info("统计结果：{}", statistics);

        return statistics;
    }

    public Map<String, String> getCityMap() {
        Map<String, String> cities = citiesConfig.getCities();

        //对键值对进行排序
        LinkedHashMap<String, String> collect = cities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        return collect;
    }
}