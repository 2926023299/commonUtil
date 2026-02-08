package com.tool.otsutil.service.InspectionImpl;

import cn.hutool.core.util.ObjectUtil;
import com.tool.otsutil.config.CommandsConfig;
import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.mapper.InspectionTableMapper;
import com.tool.otsutil.model.dto.inspection.InspectionCommon;
import com.tool.otsutil.model.dto.inspection.InspectionHeader;
import com.tool.otsutil.model.dto.inspection.InspectionResult;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.model.entity.InspectionTable;
import com.tool.otsutil.util.ExcelExportUtil;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class InspectionService {

	@Autowired
	private InspectionConfig inspectionConfig;

	@Autowired
	private ExcelExportUtil excelExportUtil;

	@Autowired
	InspectionHeader inspectionHeader;

	@Autowired
	private CommandsConfig commandsConfig;

	@Autowired
	private InspectionTableMapper inspectionTableMapper;

	//进行各个服务器命令巡检
	public LinkedHashMap<String, InspectionResult> performInspection() {
		List<ServerConfig> servers = getServerConfigs();
		LinkedHashMap<String, InspectionResult> inspectionResultMap = new LinkedHashMap<>();

		InspectionResult inspectionResult;

		log.info("开始进行服务器巡检");

		for (ServerConfig server : servers) {
			try (SSHClient sshClient = connectToServer(server)) {
				//获取cpu使用率
				String cpuUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.CPU_USAGE)).replace("\n", "");
				//获取内存使用率
				String memoryTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.MEMORY_TOTAL)).replace("\n", "");
				//获取内存使用量
				String memoryUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.MEMORY_USAGE)).replace("\n", "");
				//获取内存使用率
				String memoryUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.MEMORY_USAGE_RATE)).replace("\n", "");
				//获取磁盘总量
				String diskTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.DISK_TOTAL)).replace("\n", "");
				//获取磁盘使用量
				String diskUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.DISK_USAGE)).replace("\n", "");
				//获取磁盘使用率
				String diskUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.DISK_USAGE_RATE)).replace("\n", "");
				// 获取线程数
				String threadCount = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.THREAD_COUNT)).replace("\n", "");
				// 获取java进程,并去掉最后一个换行符
				String jpsPath = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.JPS_PATH)).replace("\n", "");
				if (ObjectUtil.isEmpty(jpsPath) || jpsPath.isEmpty()) {
					jpsPath = "/usr/local/jdk1.8.0_121/bin/jps";
				}
				String javaProcesses = executeCommand(sshClient, jpsPath + commandsConfig.getCommand().get(InspectionCommon.JAVA_PROCESSES));
				if (ObjectUtil.isEmpty(javaProcesses)) {
					jpsPath = "/usr/local/jdk1.8.0_202/bin/jps";
					javaProcesses = executeCommand(sshClient, jpsPath + commandsConfig.getCommand().get(InspectionCommon.JAVA_PROCESSES));
				}
				if (ObjectUtil.isEmpty(javaProcesses)) {
					jpsPath = "~/jdk1.8/bin/jps";
					javaProcesses = executeCommand(sshClient, jpsPath + commandsConfig.getCommand().get(InspectionCommon.JAVA_PROCESSES));
				}

				// 去除最后一个换行符
				if (ObjectUtil.isNotEmpty(javaProcesses) && javaProcesses.endsWith("\n")) {
					javaProcesses = javaProcesses.substring(0, javaProcesses.length() - 1);


				}

				String secondDiskUsage = "";
				String secondDiskTotal = "";
				String secondDiskUsageRate = "";
				if (!Objects.equals(server.getIp(), "25.87.14.209")) {
					//获取第二块磁盘使用量
					secondDiskUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_USAGE)).replace("\n", "");
					// 获取第二块磁盘使用率
					secondDiskUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_USAGE_RATE)).replace("\n", "");
					//获取第二块磁盘总量
					secondDiskTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_TOTAL)).replace("\n", "");

				}

				String thirdDiskUsage = "";
				String thirdDiskTotal = "";
				String thirdDiskUsageRate = "";
				if (Objects.equals(server.getIp(), "25.87.14.209")) {
					//获取第二块磁盘使用量
					secondDiskUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_USAGE_209)).replace("\n", "");
					// 获取第二块磁盘使用率
					secondDiskUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_USAGE_RATE_209)).replace("\n", "");
					//获取第二块磁盘总量
					secondDiskTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_TOTAL_209)).replace("\n", "");

					// 第三块硬盘
					thirdDiskUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.THIRD_SECOND_DISK_USAGE)).replace("\n", "");
					thirdDiskUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.THIRD_DISK_USAGE_RATE)).replace("\n", "");
					thirdDiskTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.THIRD_SECOND_DISK_TOTAL)).replace("\n", "");

				}

				//封装写入mysql的数据
				InspectionTable inspectionTables = new InspectionTable();
				inspectionTables.setIP(server.getIp());
				inspectionTables.setCPU_USAGE(cpuUsage);
				inspectionTables.setMEMORY_USAGE_RATE(memoryUsageRate);
				inspectionTables.setMEMORY_USAGE(memoryUsage);
				inspectionTables.setMEMORY_TOTAL(memoryTotal);
				inspectionTables.setDISK_USAGE_RATE(diskUsageRate.replace("%",""));
				inspectionTables.setDISK_USAGE(diskUsage);
				inspectionTables.setDISK_TOTAL(diskTotal);
				inspectionTables.setTHREAD_COUNT(threadCount);
				inspectionTables.setJAVA_PROCESSES(javaProcesses);
				inspectionTables.setSECOND_DISK_USAGE_RATE(secondDiskUsageRate.replace("%",""));
				inspectionTables.setSECOND_DISK_USAGE(secondDiskUsage);
				inspectionTables.setSECOND_DISK_TOTAL(secondDiskTotal);
				inspectionTables.setTHIRD_DISK_USAGE_RATE(thirdDiskUsageRate.replace("%",""));
				inspectionTables.setTHIRD_DISK_USAGE(thirdDiskUsage);
				inspectionTables.setTHIRD_DISK_TOTAL(thirdDiskTotal);

				String description = "";
				double cpuUsageTmp = ObjectUtil.isEmpty(inspectionTables.getCPU_USAGE()) ? 0 : Double.parseDouble(inspectionTables.getCPU_USAGE());
				double memoryUsageRateTmp = ObjectUtil.isEmpty(inspectionTables.getMEMORY_USAGE_RATE()) ? 0 : Double.parseDouble(inspectionTables.getMEMORY_USAGE_RATE());
				double diskUsageRateTmp = ObjectUtil.isEmpty(inspectionTables.getDISK_USAGE_RATE()) ? 0 : Double.parseDouble(inspectionTables.getDISK_USAGE_RATE());
				double secondDiskUsageRateTmp = ObjectUtil.isEmpty(inspectionTables.getSECOND_DISK_USAGE_RATE()) ? 0 : Double.parseDouble(inspectionTables.getSECOND_DISK_USAGE_RATE());
				double thirdDiskUsageRateTmp = ObjectUtil.isEmpty(inspectionTables.getTHIRD_DISK_USAGE_RATE()) ? 0 : Double.parseDouble(inspectionTables.getTHIRD_DISK_USAGE_RATE());

				// 设置告警状态和描述
				if (cpuUsageTmp >= 90 || memoryUsageRateTmp >= 90 || diskUsageRateTmp >= 90 || secondDiskUsageRateTmp >= 90 || thirdDiskUsageRateTmp >= 90) {
					inspectionTables.setStatus(2);
					if (cpuUsageTmp >= 90) description += " CPU严重告警 ";
					if (memoryUsageRateTmp >= 90) description += " 内存严重告警 ";
					if (diskUsageRateTmp >= 90) description += " 磁盘一严重告警 ";
					if (secondDiskUsageRateTmp >= 90) description += " 磁盘二严重告警 ";
					if (thirdDiskUsageRateTmp >= 90) description += " 磁盘三严重告警 ";
				} else if (cpuUsageTmp > 80 || memoryUsageRateTmp >= 80 || diskUsageRateTmp >= 80 || secondDiskUsageRateTmp >= 80 || thirdDiskUsageRateTmp >= 80) {
					inspectionTables.setStatus(1);
					if (cpuUsageTmp > 80) description += " CPU使用率告警 ";
					if (memoryUsageRateTmp >= 80) description += " 内存使用率告警 ";
					if (diskUsageRateTmp >= 80) description += " 磁盘一使用率告警 ";
					if (secondDiskUsageRateTmp >= 80) description += " 磁盘二使用率告警 ";
					if (thirdDiskUsageRateTmp >= 80) description += " 磁盘三使用率告警 ";
				} else {
					inspectionTables.setStatus(0);
					description = "正常";
				}

				inspectionTables.setDescription(description);
				System.out.println("写入mysql: " + inspectionTables);
				// 写入mysql中
				inspectionTableMapper.insert(inspectionTables);


				//进行结果的封装
				diskUsage = diskUsage + "/" + diskTotal;
				memoryUsage = memoryUsage + "/" + memoryTotal;
				secondDiskUsage = secondDiskUsage + "/" + secondDiskTotal;
				thirdDiskUsage = thirdDiskUsage + "/" + thirdDiskTotal;

				Map<String, String> result = new HashMap<>();
				result.put(InspectionCommon.IP, server.getIp());  // ip
				result.put(InspectionCommon.CPU_USAGE, cpuUsage); // cpu使用率
				result.put(InspectionCommon.MEMORY_USAGE_RATE, memoryUsageRate); // 内存使用率
				result.put(InspectionCommon.MEMORY_USAGE, memoryUsage); // 内存使用量
				result.put(InspectionCommon.DISK_USAGE_RATE, diskUsageRate); // 磁盘使用率
				result.put(InspectionCommon.DISK_USAGE, diskUsage); // 磁盘使用量
				result.put(InspectionCommon.THREAD_COUNT, threadCount); // 线程数
				result.put(InspectionCommon.JAVA_PROCESSES, javaProcesses); // java进程
				result.put(InspectionCommon.MEMORY_TOTAL, memoryTotal); // 内存总量
				result.put(InspectionCommon.DISK_TOTAL, diskTotal); // 磁盘总量
				result.put(InspectionCommon.SECOND_DISK_USAGE_RATE, secondDiskUsageRate); // 第二块磁盘使用率
				result.put(InspectionCommon.SECOND_DISK_USAGE, secondDiskUsage); // 第二块磁盘使用量
				result.put(InspectionCommon.SECOND_DISK_TOTAL, secondDiskTotal); // 第二块磁盘总量
				result.put(InspectionCommon.THIRD_DISK_USAGE_RATE, thirdDiskUsageRate);
				result.put(InspectionCommon.THIRD_SECOND_DISK_USAGE, thirdDiskUsage);
				result.put(InspectionCommon.THIRD_SECOND_DISK_TOTAL, thirdDiskTotal);

				log.info("获取服务器:{}信息:{}", server.getIp(), result);

				inspectionResult = new InspectionResult(result);

				inspectionResultMap.put(server.getIp(), inspectionResult);
			} catch (Exception e) {
				inspectionResultMap.put(server.getIp(), new InspectionResult());
				e.printStackTrace();
			}
		}

		return inspectionResultMap;
	}

	// 导入巡检日志excel到
	public void exportInspectionToExcel(String fileName, String sheetName) throws Exception {
		LinkedHashMap<String, InspectionResult> inspectionResultMap = performInspection();

		Map<String, LinkedList<String>> headers = inspectionHeader.getHeaders();

		// 获取模拟数据
		//LinkedHashMap<String, InspectionResult> inspectionResultMap = getMock();

		excelExportUtil.exportInspectionToExcel(fileName, sheetName, headers, inspectionResultMap);
		log.info("服务器资源巡检结果导出成功");
	}

	//获取ssh连接
	public SSHClient connectToServer(ServerConfig server) throws IOException {
		SSHClient sshClient = null;
		try {
			sshClient = new SSHClient();
			sshClient.addHostKeyVerifier(new PromiscuousVerifier());
			sshClient.connect(server.getIp(), server.getPort());
			sshClient.authPassword(server.getUsername(), server.getPassword());
		} catch (IOException e) {
			log.error(":服务器{}连接错误", server.getIp());
			throw new IOException(e);
		}
		return sshClient;
	}

	//执行命令
	public String executeCommand(SSHClient sshClient, String command) throws IOException {
		log.debug(":执行命令:{}", command);
		Session session;

		session = sshClient.startSession();
		Session.Command cmd = session.exec(command);
		String result = IOUtils.readFully(cmd.getInputStream()).toString();
		String errString = IOUtils.readFully(cmd.getErrorStream()).toString();
		if (!errString.isEmpty()) {
			log.error(":命令执行错误:{}", errString);
		}
		
		cmd.join();
		session.close();
		return result;
	}

	//执行命令
	// public String executeCommand(SSHClient sshClient, String command) throws IOException {

	// 	return executeCommand(sshClient, command, 30); // 默认30秒超时
	// }

	/**
	 * 执行远程命令，支持超时设置，同时读取标准输出和错误输出
	 *  搞不懂为什么服务器巡检的时候就会报错which jps找不到，很操蛋，服务器巡检用上面一版
	 * @param sshClient SSH客户端
	 * @param command 要执行的命令
	 * @param timeoutSeconds 超时时间（秒）
	 * @return 命令执行结果
	 * @throws IOException 如果命令执行失败或超时
	 */
	public String executeCommand(SSHClient sshClient, String command, int timeoutSeconds) throws IOException {
		log.debug(":执行命令:{}, 超时时间:{}秒", command, timeoutSeconds);
		Session session = null;
		Session.Command cmd = null;
		StringBuilder resultBuilder = new StringBuilder();
		StringBuilder errorBuilder = new StringBuilder();

		try {
			session = sshClient.startSession();
			cmd = session.exec(command);
			
			final Session.Command finalCmd = cmd;
			
			// 直接读取输出，不使用线程池
			String stdout = IOUtils.readFully(finalCmd.getInputStream()).toString();
			String stderr = IOUtils.readFully(finalCmd.getErrorStream()).toString();
			cmd.join();
			
			resultBuilder.append(stdout);
			errorBuilder.append(stderr);

			// 等待命令执行完成
			long startTime = System.currentTimeMillis();
			while (!finalCmd.isEOF()) {
				if (System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
					finalCmd.close();
					throw new IOException("命令执行超时: " + command);
				}
				Thread.sleep(100);
			}

			// 检查命令退出码
			int exitCode = finalCmd.getExitStatus();
			if (exitCode != 0) {
				String errorMsg = String.format("命令执行失败 (退出码: %d): %s%n错误输出: %s", 
					exitCode, command, errorBuilder.toString());
				log.error(errorMsg);
				throw new IOException(errorMsg);
			}

			// 如果有错误输出，记录警告
			if (errorBuilder.length() > 0) {
				log.warn("命令执行成功但有错误输出: {}", errorBuilder.toString());
			}

			return resultBuilder.toString();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("命令执行被中断: " + command, e);
		} finally {
			if (cmd != null) {
				cmd.close();
			}
			if (session != null) {
				session.close();
			}
		}
	}

	//获取ssh的服务器配置
	public List<ServerConfig> getServerConfigs() {
		return inspectionConfig.getServers();
	}

	public void exportJavaInspectionToExcel(String fileName) throws Exception {
		LinkedHashMap<String, InspectionResult> inspectionResultMap = performInspection();

		excelExportUtil.exportJavaInspectionToExcel(fileName, inspectionResultMap);
		inspectionResultMap.forEach((key, value) -> {
			log.info("导出ip:{}, java:{}", value.getResultInfo().get(InspectionCommon.IP), value.getResultInfo().get(InspectionCommon.JAVA_PROCESSES));
		});
	}
}
