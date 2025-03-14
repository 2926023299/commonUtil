package com.tool.otsutil.util;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.ApiDto.RateResult;
import com.tool.otsutil.model.dto.inspection.InspectionCommon;
import com.tool.otsutil.model.dto.inspection.InspectionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class ExcelExportUtil {

	@Value("${excel.export-path}")
	private String exportPath;

	/**
	 * 导出 Excel 文件（横向结构，第一行 IP，第二行字段，第三行数据）
	 *
	 * @param fileName 文件名
	 * @param headers  表头字段（如 CPU、内存等）
	 * @param data     数据列表，每个 Map 对应一个 IP 的数据
	 * @throws IOException 如果文件保存失败
	 */
	public void exportInspectionToExcel(String fileName, Map<String, LinkedList<String>> headers, LinkedHashMap<String, InspectionResult> data) throws Exception {
		Workbook workbook = new XSSFWorkbook();

		//判断是否存在文件
		String filePath = exportPath + File.separator + fileName;
		File file = new File(filePath);

		// 判断文件是否存在
		if (file.exists()) {
			// 打开文件
			FileInputStream fis = new FileInputStream(file);
			workbook = new XSSFWorkbook(fis);
			Sheet sheet = workbook.getSheet("服务器资源占用巡检");

			// 判断是否存在sheet
			if (sheet != null) {

				writeInspectionSheet(headers, data, workbook, sheet);

				// 保存回文件
				try (FileOutputStream fos = new FileOutputStream(file)) {
					workbook.write(fos);
				}

			} else {
				//sheet不存在，则写入新的sheet
				sheet = workbook.createSheet("服务器资源占用巡检");

				//设置风格
				CellStyle cellStyle = setGlobalStyle(workbook);

				writeInspectionNewSheet(headers, data, sheet, cellStyle);

				// 保存回文件
				try (FileOutputStream fos = new FileOutputStream(file)) {
					workbook.write(fos);
				}
			}

			log.info("servers巡检文件保存成功：{}", filePath);
			return;
		}

		//文件不存在，则写入新的文件
		Sheet sheet = workbook.createSheet("服务器资源占用巡检");
		//设置风格
		CellStyle cellStyle = setGlobalStyle(workbook);
		writeInspectionNewSheet(headers, data, sheet, cellStyle);
		// 保存文件到指定路径
		String path = exportExcelToPath(fileName, workbook);
		log.info("servers巡检文件保存成功：{}", path);
		workbook.close();
	}

	// 设置全局的单元格样式
	public CellStyle setGlobalStyle(Workbook workbook) {
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

		return cellStyle;
	}

	/**
	 * @param headers   表头字段（如 CPU、内存等）
	 * @param data      数据列表，每个 Map 对应一个 IP 的数据
	 * @param sheet     表
	 * @param cellStyle 单元格样式
	 * @throws IOException 异常抛出
	 */
	private void writeInspectionNewSheet(Map<String, LinkedList<String>> headers, LinkedHashMap<String, InspectionResult> data, Sheet sheet, CellStyle cellStyle) throws IOException {
		// 获取当前日期
		LocalDate currentDate = LocalDate.now();
		// 格式化日期和星期
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd EEEE", Locale.CHINA);
		// 输出结果
		String formattedDate = currentDate.format(formatter);

		int rowIndex = 0;
		// 定义行索引
		Row ipRow = sheet.createRow(rowIndex++);
		Row headerRow = sheet.createRow(rowIndex++);
		Row resultRow = sheet.createRow(rowIndex);

		//定义列索引从第一列开始
		int ipColIndex = 0;
		int headerColIndex = 0;
		int resultColIndex = 0;
		for (String ip : data.keySet()) {
			int startCol = ipColIndex + 1;
			int endCol = ipColIndex + headers.get(ip).size();

			// 创建第一行（IP 行，合并单元格）
			//写入标题
			Cell cell = ipRow.createCell(ipColIndex);

			if (ipColIndex == 0) {
				cell.setCellValue(InspectionCommon.IP);

			}

			//写入ip并合并单元格
			sheet.addMergedRegion(new CellRangeAddress(0, 0, startCol, endCol));
			cell = ipRow.createCell(startCol);
			cell.setCellStyle(cellStyle); // 应用样式
			cell.setCellValue(ip);

			ipColIndex += headers.get(ip).size();

			InspectionResult InspectionResultMap = data.get(ip);

			for (String header : headers.get(ip)) {
				// 写入第二行标题
				if (headerColIndex == 0) {
					cell = headerRow.createCell(headerColIndex++);
					cell.setCellStyle(cellStyle); // 应用样式
					cell.setCellValue("指标");
				}

				// 创建第二行（字段名行）
				cell = headerRow.createCell(headerColIndex++);
				cell.setCellStyle(cellStyle); // 应用样式
				cell.setCellValue(header);

				//创建第三行
				//写入第三行数据日期
				if (resultColIndex == 0) {
					cell = resultRow.createCell(resultColIndex++);
					cell.setCellStyle(cellStyle); // 应用样式
					cell.setCellValue(formattedDate);
				}

				cell = resultRow.createCell(resultColIndex++);
				cell.setCellStyle(cellStyle); // 应用样式
				cell.setCellValue(InspectionResultMap.getResultInfo().get(header)); // 如果数据不足，用空字符串填充
			}
		}
	}

	private void writeInspectionSheet(Map<String, LinkedList<String>> headers, LinkedHashMap<String, InspectionResult> data, Workbook workbook, Sheet sheet) throws Exception {
		// 获取当前日期
		LocalDate currentDate = LocalDate.now();
		// 格式化日期和星期
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd EEEE", Locale.CHINA);
		// 输出结果
		String formattedDate = currentDate.format(formatter);

		int lastRowIndex = sheet.getLastRowNum(); // 获取最后一行的索引
		Row newRow = sheet.createRow(lastRowIndex + 1); // 创建新的一行


		int resultColIndex = 0; // 第一列是日期
		for (String ip : data.keySet()) {

			//巡检的常量，数值
			Map<String, String> resultInfo = data.get(ip).getResultInfo();

			// 写入数据
			for (String header : headers.get(ip)) {

				CellStyle cellStyle = setGlobalStyle(workbook);

				if (resultColIndex == 0) { // 设置第一列的日期
					Cell cell = newRow.createCell(resultColIndex++);
					cell.setCellStyle(cellStyle); // 应用样式
					cell.setCellValue(formattedDate);
				}

				//通过header获取巡检结果.
				String value = resultInfo.get(header);

				//写入数据
				Cell cell = newRow.createCell(resultColIndex++);

				// 设置背景颜色为白色
				cellStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
				cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

				//第二块硬盘单独判断，可能float会解析失败抛出异常
				setSecondColor(header, value, cellStyle);

				// 判断CPU是否超过80%
				if (value != null && !StrUtil.isBlank(value)) {
					if (Objects.equals(header, InspectionCommon.CPU_USAGE) && Float.parseFloat(value) > 80) {
						// 设置背景颜色为黄色
						cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
						cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
					} else if (Objects.equals(header, InspectionCommon.MEMORY_USAGE)) {
						String[] split = value.split("/");
						value = value.replace("G", "").replace("M", "");

						//判断单位是否为M
						if (split[0].contains("M")) {
							split[0] = split[0].replace("M", "");
							split[0] = String.valueOf(Float.parseFloat(split[0]) / 1024);
						}

						if (Float.parseFloat(split[0].replace("G", "")) / Float.parseFloat(split[1].replace("G", "")) > 0.8) {
							// 设置背景颜色为黄色
							cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
							cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
						}
					} else if (Objects.equals(header, InspectionCommon.DISK_USAGE_RATE) && Float.parseFloat(value.replace("%", "")) > 80) {

						cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
						cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
					} else if (Objects.equals(header, InspectionCommon.THREAD_COUNT) && Integer.parseInt(value.replace("\n", "")) > 3000) {
						cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
						cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
					}
				}

				// 应用样式到单元格
				cell.setCellStyle(cellStyle);
				cell.setCellValue(value);
			}
		}

	}

	private static void setSecondColor(String header, String value, CellStyle cellStyle) {
		try {
			if (StrUtil.isNotEmpty(value) && StrUtil.isNotEmpty(value)) {
				if (Objects.equals(header, InspectionCommon.SECOND_DISK_USAGE_RATE) && Float.parseFloat(value.replace("%", "")) > 80) {
					cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
					cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				} else if (Objects.equals(header, InspectionCommon.THIRD_DISK_USAGE_RATE) && Float.parseFloat(value.replace("%", "")) > 80) {
					cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
					cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				}
			}
		} catch (NumberFormatException e) {
			log.error("数据格式错误{}", value);
		}
	}

	// 保存文件到指定路径
	private String exportExcelToPath(String fileName, Workbook workbook) throws IOException {
		File directory = new File(exportPath);
		if (!directory.exists()) {
			boolean mkdir = directory.mkdirs();
			if (!mkdir) {
				throw new IOException("创建目录失败:" + exportPath);
			}
		}

		String fullPath = exportPath + File.separator + fileName;
		try (FileOutputStream fos = new FileOutputStream(fullPath)) {
			workbook.write(fos);
		}

		return fullPath;
	}

	// 导出率指标到巡检记录表中
	public void exportRateToInspectionExcel(String fileName, LinkedHashMap<String, String> areas, List<RateResult> rateResults) throws IOException {

		log.info("开始导出率指标到巡检记录表中");


		// 循环率指标数据把rate修改为百分比形式，保留小数点两位
		rateResults.forEach(rateResult -> {
			if (rateResult.getRate().intValue() < 1) {
				rateResult.setRate(new BigDecimal(rateResult.getRate().multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toString()));
			}
		});

		log.info("导出数据展示:{}", rateResults);

		Workbook workbook = getWorkbook(fileName);

		Sheet sheet = workbook.getSheet("系统界面巡检");

		// 判断是否存在sheet
		if (ObjectUtil.hasNull(sheet)) {
			throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "系统界面巡检sheet不存在");
		}
		if (sheet.getPhysicalNumberOfRows() == 0) {
			throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "系统界面巡检sheet为空");
		}
		// 获取当前日期
		LocalDate currentDate = LocalDate.now();
		// 格式化日期和星期
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd EEEE", Locale.CHINA);
		// 输出结果
		String formattedDate = currentDate.format(formatter);


		int lastRowIndex = sheet.getLastRowNum(); // 获取最后一行的索引

		//定位到最后的那一行,然后复制一行
		Row oldRow = sheet.getRow(lastRowIndex);
		Row newRow = sheet.createRow(lastRowIndex + 1);

		// 复制行到newRow
		copyRow(oldRow, newRow);

		CellStyle cellStyle = this.setGlobalStyle(workbook);

		int resultColIndex = 0; // 第一列是日期

		// 获取所有区域<areaId, areaAlisaName>

		for (RateResult rateResult : rateResults) {

			if (resultColIndex == 0) { // 设置第一列的日期
				Cell cell = newRow.createCell(resultColIndex++);
				cell.setCellStyle(cellStyle);
				cell.setCellValue(formattedDate);
				resultColIndex += 74;
			}

			for (String areaId : areas.keySet()) {

				if (areaId.equals(rateResult.getAreaId())) {
					Cell cell = newRow.createCell(resultColIndex++);
					cell.setCellStyle(cellStyle);
					cell.setCellValue(rateResult.getRate() + "%");
				}
			}
		}

		// 保存文件到指定路径
		String path = exportExcelToPath(fileName, workbook);
		log.info("率指标导出保存成功：{}", path);
		workbook.close();
	}

	// 通过文件获取workbook对象
	private @NotNull Workbook getWorkbook(String fileName) {
		Workbook workbook;

		//判断是否存在文件
		String filePath = exportPath + File.separator + fileName;
		File file = new File(filePath);

		if (file.exists()) {
			try (FileInputStream fis = new FileInputStream(file)) {
				workbook = new XSSFWorkbook(fis); // 加载已有文件
			} catch (IOException e) {
				throw new RuntimeException("加载 Excel 文件失败: " + filePath, e);
			}
		} else {
			throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "excel文件不存在,写入失败");
		}
		return workbook;
	}

	/**
	 * 复制行
	 *
	 * @param sourceRow 源行
	 * @param newRow    目标行
	 */
	private static void copyRow(Row sourceRow, Row newRow) {
		for (Cell sourceCell : sourceRow) {
			Cell newCell = newRow.createCell(sourceCell.getColumnIndex());
			switch (sourceCell.getCellType()) {
				case STRING:
					newCell.setCellValue(sourceCell.getStringCellValue());
					break;
				case NUMERIC:
					newCell.setCellValue(sourceCell.getNumericCellValue());
					break;
				case BOOLEAN:
					newCell.setCellValue(sourceCell.getBooleanCellValue());
					break;
				case FORMULA:
					newCell.setCellFormula(sourceCell.getCellFormula());
					break;
				default:
					break;
			}
			// 复制单元格样式
			CellStyle style = sourceCell.getCellStyle();
			newCell.setCellStyle(style);
		}
	}

	// 拷贝列
	public static void copyColumn(Cell sourceCell, Cell newCell) {
		if (sourceCell == null || newCell == null) {
			throw new IllegalArgumentException("Source and target cells cannot be null");
		}

		// 拷贝单元格值及类型
		CellType cellType = sourceCell.getCellType();
		newCell.setCellType(cellType);

		switch (cellType) {
			case STRING:
				newCell.setCellValue(sourceCell.getStringCellValue());
				break;
			case NUMERIC:
				newCell.setCellValue(sourceCell.getNumericCellValue());
				break;
			case BOOLEAN:
				newCell.setCellValue(sourceCell.getBooleanCellValue());
				break;
			case FORMULA:
				newCell.setCellFormula(sourceCell.getCellFormula());
				break;
			case ERROR:
				newCell.setCellErrorValue(sourceCell.getErrorCellValue());
				break;
			default:
				newCell.setCellValue("");
		}

		// 拷贝单元格样式（复用样式对象）
		CellStyle sourceStyle = sourceCell.getCellStyle();
		newCell.setCellStyle(sourceStyle);

		// 处理合并单元格（需要工作表层面处理，此处添加TODO标记）
		// TODO: 需要结合Sheet.addMergedRegion()处理合并单元格逻辑
	}

	// 导出包含 Java 程序信息的巡检日志到 Excel
	public void exportJavaInspectionToExcel(String fileName, LinkedHashMap<String, InspectionResult> inspectionResultMap) throws IOException {
		Workbook workbook = getWorkbook(fileName);

		Sheet sheet = workbook.getSheet("程序巡检");

		// 判断是否存在sheet
		if (ObjectUtil.hasNull(sheet)) {
			log.info("sheet不存在，创建新的sheet:程序巡检");
			sheet = workbook.createSheet("程序巡检");
		}

		if (sheet == null) {
			throw new IllegalStateException("无法创建工作表");
		}

		// 创建表头
		Row headerRow = sheet.getRow(0);
		if (headerRow == null) {
			headerRow = sheet.createRow(0);
			String[] headers = {InspectionCommon.IP, "之前的 Java 程序记录", "当前的 Java 程序记录", "是否diff"};
			for (int i = 0; i < headers.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(headers[i]);
			}
		}

		// 复制第三列到第二列
		int lastRowNum = sheet.getLastRowNum();
		for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
			Row row = sheet.getRow(rowIndex);
			if (row != null) {
				Cell sourceCell = row.getCell(2);
				Cell targetCell = row.getCell(1);
				if (targetCell == null) {
					targetCell = row.createCell(1);
				}
				if (sourceCell != null) {
					copyColumn(sourceCell, targetCell);
				}
			}
		}

		// 填充新的巡检数据到第三列
		int rowIndex = 1;
		for (Map.Entry<String, InspectionResult> entry : inspectionResultMap.entrySet()) {
			Row row = sheet.getRow(rowIndex);
			if (row == null) {
				row = sheet.createRow(rowIndex);
			}
			Map<String, String> resultInfo = entry.getValue().getResultInfo();

			// 写入 IP
			Cell ipCell = row.getCell(0);
			if (ipCell == null) {
				ipCell = row.createCell(0);
			}
			ipCell.setCellValue(entry.getKey());

			// 写入当前的 Java 程序记录
			Cell currentJavaProcessCell = row.getCell(2);
			if (currentJavaProcessCell == null) {
				currentJavaProcessCell = row.createCell(2);
			}
			String currentValue = resultInfo.getOrDefault(InspectionCommon.JAVA_PROCESSES, "");
			currentJavaProcessCell.setCellValue(currentValue);

			// 新增差异比对逻辑
			Cell diffCell = row.createCell(3); // 第四列
			CellStyle cellStyle = setGlobalStyle(workbook); // 使用全局样式
			//设置左对齐
			cellStyle.setAlignment(HorizontalAlignment.LEFT);
			diffCell.setCellStyle(cellStyle);

			// 获取第二列（历史记录）和第三列（当前记录）的值
			Cell previousCell = row.getCell(1);
			String previousValue = (previousCell != null && previousCell.getCellType() == CellType.STRING) ? previousCell.getStringCellValue() : "";

			// 比对差异
			if (ObjectUtil.isNull(currentValue) || ObjectUtil.isNull(previousValue)) {
				cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
				cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				diffCell.setCellValue("全null异常");
			}
			else if (StrUtil.isBlank(currentValue) && StrUtil.isBlank(previousValue)) {
				cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
				cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				diffCell.setCellValue("全空值异常");
			}
			else if (!currentValue.equals(previousValue)) {
				diffCell.setCellValue(getStringDifference(previousValue, currentValue));
				cellStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
				cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
				diffCell.setCellStyle(cellStyle);
			} else {
				diffCell.setCellValue("否");
			}

			rowIndex++;
		}

		// 保存文件
		exportExcelToPath(fileName, workbook);

		workbook.close();

		log.info("Java 巡检日志导出成功");
	}

	// 新增差异比对方法
	private String getStringDifference(String base, String current) {
		if (base == null || base.isEmpty()) return current;
		if (current == null || current.isEmpty()) return "空值差异";

		// 简单实现：返回新增的不同行（按换行符分割）
		Set<String> baseSet = new HashSet<>(Arrays.asList(base.split("\n")));
		Set<String> currentSet = new HashSet<>(Arrays.asList(current.split("\n")));
		baseSet.removeAll(currentSet);
		return baseSet.isEmpty() ? "否" : String.join("\n", baseSet);
	}
}