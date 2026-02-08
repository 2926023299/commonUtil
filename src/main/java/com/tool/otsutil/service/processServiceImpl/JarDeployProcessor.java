package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.FileProcessor;
import com.tool.otsutil.service.InspectionImpl.InspectionService;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.model.fileProcess.FileEvent;

import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.stream.Stream;

@Component
@Slf4j
public class JarDeployProcessor implements FileProcessor {

	@Value("${jar.target.path}")
	private String targetPath;

	@Value("${jar.nginx.path}")
	private String nginxPath;

	@Autowired
	private InspectionConfig jarDeployConfig;

	@Autowired
	private InspectionService inspectionService;

	private FileMonitorConfig fileMonitorConfig;

	@Override
	public String getName() {
		return "jarDeploy-processor";
	}

	@Override
	public boolean supports(String filePath, boolean isStable) {
		return (filePath.toLowerCase().endsWith(".jar") || filePath.toLowerCase().endsWith(".zip")) && isStable;
	}

	@Override
	public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
		fileMonitorConfig = config;

		try {
			log.info("[Jar包部署] 触发部署任务: {}", filePath);
			autoScanAndDeploy(Paths.get(filePath));
			return ProcessingResult.success("Jar包部署成功");
		} catch (Exception e) {
			log.error("[Jar包部署] 部署失败: {}", filePath, e);
			// 部署失败后移动文件到error目录
			moveProcessedFile(Paths.get(filePath), false);
			return ProcessingResult.failure("Jar包部署失败", e);
		}
	}

	public boolean autoScanAndDeploy(Path path) {

		if (path.getFileName().toString().endsWith(".jar")) {
			try {
				deployJar(path);
				// JAR包部署成功后移动到backup目录
				moveProcessedFile(path, true);
			} catch (Exception e) {
				log.error("[扫描任务] 部署 JAR 包 {} 失败: {}", path.getFileName(), e.getMessage(), e);
				// JAR包部署失败后移动到error目录
				moveProcessedFile(path, false);
				throw new RuntimeException("部署 JAR 包失败", e);
			}
		} else if (path.getFileName().toString().endsWith(".zip")) {
			try {
				log.info("[扫描任务] 发现前端包: {}", path.getFileName());
				deployNginx(path);
				// 前端包部署成功后移动到backup目录
				moveProcessedFile(path, true);
			} catch (Exception e) {
				log.error("[扫描任务] 部署前端包 {} 失败: {}", path.getFileName(), e.getMessage(), e);
				// 前端包部署失败后移动到error目录
				moveProcessedFile(path, false);
				throw new RuntimeException("部署前端包失败", e);
			}
		}
		
		return true;
	}

	private void deployNginx(Path zipPath) {
		log.info("[前端部署] 开始部署前端包: {}", zipPath.getFileName());
		if (!Files.exists(zipPath)) {
			log.warn("[前端部署] 文件 {} 不存在，跳过部署。", zipPath);
			return;
		}

		// 使用 Apache Commons Compress 解压 ZIP 文件
		Path unzipPath = null;
		try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
			unzipPath = Files.createDirectories(
					Paths.get(fileMonitorConfig.getDirectory(), zipPath.getFileName().toString().replace(".zip", "")));

			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry entry = entries.nextElement();
				String entryName = entry.getName();

				// 移除多余的一层目录
				int firstSeparatorIndex = entryName.indexOf('/');
				if (firstSeparatorIndex != -1 && firstSeparatorIndex < entryName.length() - 1) {
					entryName = entryName.substring(firstSeparatorIndex + 1); // 去掉第一层目录
				}

				Path entryPath = unzipPath.resolve(entryName);

				if (entry.isDirectory()) {
					Files.createDirectories(entryPath);
				} else {
					try (InputStream inputStream = zipFile.getInputStream(entry);
							OutputStream outputStream = Files.newOutputStream(entryPath)) {
						IOUtils.copy(inputStream, outputStream); // 复制文件内容
					}
				}
			}

			// 删除多余的空目录
			deleteEmptyDirectories(unzipPath);

			log.info("[前端部署] 解压完成，准备部署到服务器");

			String nginxFileName = unzipPath.getFileName().toString();

			boolean deploymentSuccess = false; // 标记是否至少有一个服务器部署成功

			boolean foundDeploymentTarget = false; // 标记是否找到部署目标

			for (ServerConfig serverConfig : jarDeployConfig.getServers()) {
				for (String jarEntry : serverConfig.getJars()) {
					String[] parts = jarEntry.split(":");
					String htmlName = parts[0];

					if (htmlName.equals(nginxFileName)) {
						foundDeploymentTarget = true; // 找到部署目标

						try (SSHClient sshClient = inspectionService.connectToServer(serverConfig)) {
							String serverPath = nginxPath;

							backupAndUploadJar(sshClient, unzipPath.toFile(), serverPath, nginxFileName,
								serverConfig.getIp());
							deploymentSuccess = true; // 至少有一个服务器部署成功
						} catch (Exception e) {
							log.error("部署 前端 包 {} 到目标服务器 {} 失败: {}", nginxFileName, serverConfig.getIp(),
								e.getMessage(), e);
							throw new RuntimeException("部署前端包失败", e);
						}
					}
				}
			}

			// 如果没有找到部署目标，记录警告日志
			if (!foundDeploymentTarget) {
				log.info("[前端部署] 前端包 {} 不在服务器的部署目标，请及时删除。", nginxFileName);
				// 直接返回
				return;
			}

			if (deploymentSuccess) {
				log.info("[前端部署] 前端包 {} 部署成功", zipPath.getFileName());

				// 删除解压后的文件
				try {
					Files.walk(unzipPath).sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.delete(p);
						} catch (IOException e) {
							log.error("删除失败: {}", p, e);
						}
					});
				} catch (IOException e) {
					log.error("删除解压后的文件失败: {}", e.getMessage(), e);
				}
			} else {
				log.error("[前端部署] 前端包 {} 部署失败。", zipPath.getFileName());
			}

		} catch (IOException e) {
			log.error("无法打开 ZIP 文件 {}: {}", zipPath, e.getMessage(), e);
			throw new RuntimeException("无法打开 ZIP 文件", e);
		}
	}

	// 新增方法：删除多余的空目录
	private void deleteEmptyDirectories(Path path) throws IOException {
		Files.walk(path)
				.sorted(Comparator.reverseOrder())
				.filter(Files::isDirectory)
				.forEach(dir -> {
					try {
						boolean isEmpty;
						try (Stream<Path> stream = Files.list(dir)) {
							isEmpty = !stream.findAny().isPresent();
						}
						if (isEmpty) {
							Files.deleteIfExists(dir); // 删除空目录
							log.warn("目录 {} 为空，直接删除", dir);
						}
					} catch (IOException e) {
						log.error("删除空目录失败: {}", dir, e);
					}
				});
	}

	public void deployJar(Path jarFilePath) {
		log.info("[JAR 部署] 开始部署 JAR 包: {}", jarFilePath.getFileName());
		if (!Files.exists(jarFilePath)) {
			log.warn("[JAR 部署] 文件 {} 不存在，跳过部署。", jarFilePath);
			return;
		}

		String jarFileName = jarFilePath.getFileName().toString();
		boolean deploymentSuccess = false; // 标记是否至少有一个服务器部署成功

		boolean foundDeploymentTarget = false; // 标记是否找到部署目标

		for (ServerConfig serverConfig : jarDeployConfig.getServers()) {
			for (String jarEntry : serverConfig.getJars()) {
				String[] parts = jarEntry.split(":");
				String jarName = parts[0];
				String scriptName = parts[1];

				if (jarFileName.equals(jarName)) {
					foundDeploymentTarget = true; // 找到部署目标
					try (SSHClient sshClient = inspectionService.connectToServer(serverConfig)) {
						String serverPath = targetPath;

						backupAndUploadJar(sshClient, jarFilePath.toFile(), serverPath, jarName, serverConfig.getIp());
						//stopAndStartService(inspectionService.connectToServer(serverConfig),
						// serverPath, jarName, scriptName);
						deploymentSuccess = true; // 至少有一个服务器部署成功
					} catch (Exception e) {
						log.error("部署 JAR 包 {} 到目标服务器 {} 失败: {}", jarFileName, serverConfig.getIp(), e.getMessage(), e);
					}
				}
			}
		}

		// 如果没有找到部署目标，记录警告日志
		if (!foundDeploymentTarget) {
			log.info("[JAR 部署] JAR 包 {} 不在服务器的部署目标，请及时删除。", jarFileName);
			// 直接返回
			return;
		}

		if (deploymentSuccess) {
			log.info("[JAR 部署] JAR 包 {} 部署成功", jarFilePath.getFileName());
			log.info("[JAR 部署 final] 部署程序 {} 成功", jarFilePath);
		} else {
			log.error("[JAR 部署] JAR 包 {} 部署失败。", jarFilePath.getFileName());
		}
	}

	/**
	 * 移动处理后的文件到backup或error文件夹
	 */
	private void moveProcessedFile(Path filePath, boolean isSuccess) {
		if (!Files.exists(filePath)) {
			log.warn("[文件移动] 文件 {} 不存在，跳过移动。", filePath);
			return;
		}

		// 创建backup和error文件夹
		Path monitorDir = Paths.get(fileMonitorConfig.getDirectory());
		Path backupDir = monitorDir.resolve("backup");
		Path errorDir = monitorDir.resolve("error");
		
		try {
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
			Path targetPath = targetDir.resolve(filePath.getFileName());
			
			// 移动文件
			Files.move(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			log.info("已将文件 {} 移动到 {} 目录 {}", filePath, isSuccess ? "备份" : "错误", targetDir);
			
		} catch (IOException e) {
			log.error("移动文件 {} 到 {} 目录最终失败: {}", filePath, isSuccess ? "备份" : "错误", e.getMessage(), e);
		}
	}

	private void backupAndUploadJar(SSHClient sshClient, File jarFile, String serverPath, String filename, String ip)
			throws IOException {
		String filePath = serverPath + filename;
		log.info("[文件上传] 准备备份并上传文件: {}", jarFile.getName());
		// 获取当前日期
		LocalDateTime currentDate = LocalDateTime.now();
		// 格式化日期和星期
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss", Locale.CHINA);
		// 输出结果
		String formattedDate = currentDate.format(formatter);

		String backupPath = filePath + "." + formattedDate;

		String command = "[ -d \"" + filePath + "\" ] || [ -f \"" + filePath
				+ "\" ] && echo \"exists\" || echo \"not exists\"";
		String result = inspectionService.executeCommand(sshClient, command).trim();

		if ("exists".equals(result)) {
			log.info("备份远程文件 {} 到 {}", filePath, backupPath);
			try {
				inspectionService.executeCommand(sshClient, "cp -r " + filePath + " " + backupPath);
			} catch (Exception e) {
				log.warn("备份远程文件 {} 失败: {}", filePath, e.getMessage(), e);
			}
		} else {
			log.info("远程文件 {} 不存在，无需备份", filePath);
		}

		log.info("上传本地文件 {} 到 服务器 {} 路径 {}", jarFile.getAbsolutePath(), ip, serverPath);

		try (SFTPClient sftpClient = sshClient.newSFTPClient()) {
			// sftpClient.put(jarFile.getAbsolutePath(), serverPath);

			sshClient.newSCPFileTransfer().upload(jarFile.getAbsolutePath(), serverPath);

			log.info("[文件上传] 文件 {} 上传到服务器 {} 成功。", jarFile.getName(), ip);

			if (jarFile.getName().equals("dmscldpro") || jarFile.getName().equals("dmsweb")
					|| jarFile.getName().equals("dmscldprotest") || jarFile.getName().equals("dmscldtest")
				|| jarFile.getName().equals("dmscloud") || jarFile.getName().equals("dmscldgz")) {
				try {
					inspectionService.executeCommand(sshClient, "cp  " + backupPath + "/config.js" + " " + filePath);
				} catch (Exception e) {
					log.warn("复制config.js文件失败: {}", e.getMessage(), e);
				}
			}

			if (jarFile.getName().equals("svg-ui")) {
				try {
					inspectionService.executeCommand(sshClient, "cp -r " + backupPath + "/config" + " " + filePath);
				} catch (Exception e) {
					log.warn("复制svg-ui的config文件失败: {}", e.getMessage(), e);
				}
			}

			if (jarFile.getName().equals("topo-ui")) {
				try {
					inspectionService.executeCommand(sshClient, "cp -r " + backupPath + "/config" + " " + filePath);
				} catch (Exception e) {
					log.warn("复制topo-ui的config文件失败: {}", e.getMessage(), e);
				}
			}

			return; // 成功后退出循环
		} catch (IOException e) {
			log.warn("上传文件 {} 到服务器失败，{}", jarFile.getAbsolutePath(), e.getMessage());
		}
	}

	private void stopAndStartService(SSHClient sshClient, String serverPath, String fileName, String scriptName)
			throws IOException, InterruptedException {
		String serviceName = fileName.replace(".jar", "");
		log.info("[服务管理] 停止服务: {}", serviceName);
		
		// 停止服务 - 先尝试优雅停止，失败后再强制停止
		stopServiceGracefully(sshClient, serviceName);

		log.info("[服务管理] 启动服务: {}", serviceName);
		// 启动服务 - 使用非阻塞方式
		startServiceNonBlocking(sshClient, serverPath, scriptName, serviceName);
	}

	/**
	 * 优雅停止服务
	 */
	private void stopServiceGracefully(SSHClient sshClient, String serviceName) throws IOException {
		final int MAX_WAIT_SECONDS = 30;
		final int CHECK_INTERVAL_MS = 1000;
		int waitedSeconds = 0;

		// 1. 首先尝试获取进程ID
		String getPidCommand = "bash -c 'jcmd -l | grep " + serviceName + " | awk \"{print \\$1}\"'";
		String pidStr = inspectionService.executeCommand(sshClient, getPidCommand).trim();
		
		if (pidStr.isEmpty()) {
			log.info("[服务管理] 服务 {} 未运行，无需停止", serviceName);
			return;
		}

		try {
			long pid = Long.parseLong(pidStr);
			log.info("[服务管理] 找到服务 {} 的进程ID: {}", serviceName, pid);

			// 2. 先尝试优雅停止 (SIGTERM)
			String gracefulStopCommand = "bash -c 'kill " + pid + "'";
			try {
				inspectionService.executeCommand(sshClient, gracefulStopCommand);
				log.info("[服务管理] 已发送优雅停止信号到服务 {}", serviceName);
			} catch (IOException e) {
				log.warn("[服务管理] 发送优雅停止信号失败: {}", e.getMessage());
			}

			// 3. 等待服务停止
			while (waitedSeconds < MAX_WAIT_SECONDS) {
				try {
					Thread.sleep(CHECK_INTERVAL_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					log.error("[服务管理] 等待中断: {}", e.getMessage());
					throw new RuntimeException("服务停止等待被中断", e);
				}
				waitedSeconds++;

				// 检查进程是否仍在运行
				String checkCommand = "bash -c '[ -d /proc/" + pid + " ] && echo " + pid + " || echo \"\"'";
				String result = inspectionService.executeCommand(sshClient, checkCommand).trim();
				
				if (result.isEmpty()) {
					log.info("[服务管理] 服务 {} 已成功停止，耗时 {} 秒", serviceName, waitedSeconds);
					return;
				}
				
				log.debug("[服务管理] 服务 {} 仍在运行，已等待 {} 秒...", serviceName, waitedSeconds);
			}

			// 4. 如果优雅停止超时，使用强制停止 (SIGKILL)
			log.warn("[服务管理] 服务 {} 优雅停止超时，将使用强制停止", serviceName);
			String forceStopCommand = "bash -c 'kill -9 " + pid + "'";
			try {
				inspectionService.executeCommand(sshClient, forceStopCommand);
				log.info("[服务管理] 已发送强制停止信号到服务 {}", serviceName);
				
				// 再次检查是否停止
				try {
					Thread.sleep(CHECK_INTERVAL_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					log.error("[服务管理] 等待中断: {}", e.getMessage());
					throw new RuntimeException("服务强制停止等待被中断", e);
				}
				String finalCheckCommand = "bash -c '[ -d /proc/" + pid + " ] && echo " + pid + " || echo \"\"'";
				String finalResult = inspectionService.executeCommand(sshClient, finalCheckCommand).trim();
				
				if (finalResult.isEmpty()) {
					log.info("[服务管理] 服务 {} 已被强制停止", serviceName);
					return;
				} else {
					log.error("[服务管理] 强制停止服务 {} 失败，进程 {} 仍在运行", serviceName, pid);
				}
			} catch (IOException e) {
				log.error("[服务管理] 强制停止服务 {} 失败: {}", serviceName, e.getMessage(), e);
			}
			
		} catch (NumberFormatException e) {
			log.warn("[服务管理] 无法解析进程ID: {}", pidStr);
		}
	}

	/**
	 * 非阻塞方式启动服务
	 */
	private void startServiceNonBlocking(SSHClient sshClient, String serverPath, String scriptName, String serviceName) throws IOException, InterruptedException {
		// 使用nohup和&实现非阻塞启动
		String startCommand = "bash -c 'cd " + serverPath + " && nohup ./" + scriptName + " >/dev/null 2>&1 & echo $!'";
		String pid = inspectionService.executeCommand(sshClient, startCommand).trim();
		log.info("[服务管理] 服务 {} 已启动，进程ID: {}", serviceName, pid);
		
		// 验证服务是否真正启动成功
		verifyServiceStart(sshClient, serviceName);
	}

	/**
	 * 验证服务是否启动成功
	 */
	private void verifyServiceStart(SSHClient sshClient, String serviceName) throws IOException, InterruptedException {
		final int MAX_WAIT_SECONDS = 30;
		final int CHECK_INTERVAL_MS = 2000;
		int waitedSeconds = 0;

		while (waitedSeconds < MAX_WAIT_SECONDS) {
			try {
				Thread.sleep(CHECK_INTERVAL_MS);
				waitedSeconds += 2;

				// 检查进程是否存在
				String checkCommand = "bash -c 'jcmd -l | grep " + serviceName + " | wc -l'";
				String result = inspectionService.executeCommand(sshClient, checkCommand).trim();
				
				if ("1".equals(result)) {
					log.info("[服务管理] 服务 {} 启动验证成功", serviceName);
					return;
				}
				
				log.debug("[服务管理] 服务 {} 启动验证中，已等待 {} 秒...", serviceName, waitedSeconds);
			} catch (InterruptedException e) {
				log.error("[服务管理] 服务启动验证被中断: {}", e.getMessage());
				Thread.currentThread().interrupt();
				break;
			}
		}

		log.warn("[服务管理] 服务 {} 启动验证超时，可能启动失败", serviceName);
	}
}
