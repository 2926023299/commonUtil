package com.tool.otsutil.service.processServiceImpl;

import org.springframework.beans.factory.annotation.Autowired;

import com.tool.otsutil.config.FileMonitorConfig;

import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.ConnectivityCreate;
import com.tool.otsutil.service.FileProcessor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
public class ConnectivityCreateImpl implements FileProcessor {

    @Autowired
    private ConnectivityCreate connectivityCreate;

    @Override
    public String getName() {
        return "connectivity-processor";
    }

    @Override
    public boolean supports(String filePath, boolean isStable) {
        return filePath.toLowerCase().endsWith(".xlsx") && isStable;
    }

    @Override
    public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
        log.info("开始执行处理Excel文件，将数据写入MySQL");

        connectivityCreate.processConnectivityExcel2toNewConnectivityB(filePath);
		log.info("处理Excel文件完成");
		// 移动文件到backup文件夹
		moveProcessedFile(filePath, true, config);

//        log.info("开始执行连接关系处理...");
//        try {
//            connectivityCreate.selectConnectivityForAll();
//            log.info("连接关系处理完成");
//
//        } catch (Exception e) {
//            log.error("连接关系处理失败", e);
//            return ProcessingResult.failure("文件处理失败");
//        }

        return ProcessingResult.success(filePath + "文件执行成功");
    }

	private void moveProcessedFile(String filePath, boolean isSuccess, FileMonitorConfig config) {
		try {
			Path originalFile = Paths.get(filePath);
			Path monitorDir = Paths.get(config.getDirectory());

			// 创建backup和error文件夹
			Path backupDir = monitorDir.resolve("backup");
			Path errorDir = monitorDir.resolve("error");

			if (!Files.exists(backupDir)) {
				Files.createDirectories(backupDir);
				log.info("创建备份文件夹: {}", backupDir);
			}

			if (!Files.exists(errorDir)) {
				Files.createDirectories(errorDir);
				log.info("创建错误文件夹: {}", errorDir);
			}

			// 确定目标目录
			Path targetDir = isSuccess ? backupDir : errorDir;

			// 构建目标路径
			Path targetFile = targetDir.resolve(originalFile.getFileName());

			// 移动文件
			Files.move(originalFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
			log.info("文件已移动到: {}", targetFile);

		} catch (Exception e) {
			log.error("移动文件失败: {}", filePath, e);
		}
	}
}
