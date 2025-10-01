package com.tool.otsutil.service.impl;

import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.stream.Stream;

@Slf4j
@Service
public class JarDeployService {

	@Value("${jar.scan.path}")
	private String scanPath;

	@Value("${jar.target.path}")
	private String targetPath;

	@Value("${jar.nginx.path}")
	private String nginxPath;

	@Autowired
	private InspectionConfig jarDeployConfig;

	@Autowired
	private InspectionService inspectionService;



	//private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

	private static final int MAX_RETRIES = 3;
	private static final long RETRY_DELAY_MS = 3000;

//	public JarDeployService() {
//		log.info("JarDeployService 初始化完成");
//		// 启动定时任务，每分钟扫描一次 /home/ies/gxl/dytmp/jar/ 文件夹
//		scheduledExecutorService.scheduleAtFixedRate(this::scanAndDeployJars, 0, 2, TimeUnit.MINUTES);
//	}


	// 暂时弃用，使用AutoFileMonitor自动监听
	public void scanAndDeployJars() {
		log.info("[扫描任务] 开始扫描目录: {}", scanPath);
		Path remoteJarFolderPath = Paths.get(scanPath);
		try {
			if (!Files.exists(remoteJarFolderPath)) {
				log.warn("[扫描任务] 目录 {} 不存在，跳过扫描。", scanPath);
				return;
			}
			Files.list(remoteJarFolderPath).filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
				try {
					log.info("[扫描任务] 发现 JAR 包: {}", path.getFileName());

					deployJarWithRetry(path);
				} catch (Exception e) {
					log.error("[扫描任务] 部署 JAR 包 {} 失败: {}", path.getFileName(), e.getMessage(), e);
					throw new RuntimeException("部署 JAR 包失败", e);
				}
			});

			Files.list(remoteJarFolderPath).filter(path -> path.toString().endsWith(".zip")).forEach(path -> {
				try {
					log.info("[扫描任务] 发现前端包: {}", path.getFileName());
					deployNginxWithRetry(path);
				} catch (Exception e) {
					log.error("[扫描任务] 部署前端包 {} 失败: {}", path.getFileName(), e.getMessage(), e);
					throw new RuntimeException("部署前端包失败", e);
				}
			});
		} catch (IOException e) {
			log.error("[扫描任务] 扫描目录失败: {}", e.getMessage(), e);
			throw new RuntimeException("扫描目录失败", e);
		}
	}

	public void autoScanAndDeploy(Path path) {
		log.info("[自动触发] 触发部署任务");

		if(path.getFileName().toString().endsWith(".jar")){
			try {
				log.info("[扫描任务] 发现 JAR 包: {}", path.getFileName());

				deployJarWithRetry(path);
			} catch (Exception e) {
				log.error("[扫描任务] 部署 JAR 包 {} 失败: {}", path.getFileName(), e.getMessage(), e);
				throw new RuntimeException("部署 JAR 包失败", e);
			}
		}else if(path.getFileName().toString().endsWith(".zip")){
			try {
				log.info("[扫描任务] 发现前端包: {}", path.getFileName());
				deployNginxWithRetry(path);
			} catch (Exception e) {
				log.error("[扫描任务] 部署前端包 {} 失败: {}", path.getFileName(), e.getMessage(), e);
				throw new RuntimeException("部署前端包失败", e);
			}
		}

	}

	private void deployNginxWithRetry(Path zipPath) {
		int attempt = 0;
		while (attempt < MAX_RETRIES) {
			try {
				deployNginx(zipPath);
				return; // 成功后退出循环
			} catch (Exception e) {
				attempt++;
				log.warn("deployNginxWithRetry：部署前端包 {} 失败，重试 {}/{} 次: {}", zipPath.getFileName(), attempt, MAX_RETRIES, e.getMessage());
				if (attempt < MAX_RETRIES) {
					try {
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException interruptedException) {
						Thread.currentThread().interrupt();
						log.error("重试过程中线程被中断: {}", interruptedException.getMessage());
						throw new RuntimeException("部署前端包重试被中断", interruptedException);
					}
				} else {
					log.error("部署前端包 {} 最终失败: {}", zipPath.getFileName(), e.getMessage(), e);
					throw new RuntimeException("部署前端包失败", e);
				}
			}
		}
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
			unzipPath = Files.createDirectories(Paths.get(scanPath, zipPath.getFileName().toString().replace(".zip", "")));

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

			for (ServerConfig serverConfig : jarDeployConfig.getServers()) {
				for (String jarEntry : serverConfig.getJars()) {
					String[] parts = jarEntry.split(":");
					String htmlName = parts[0];

					if (htmlName.equals(nginxFileName)) {
						try (SSHClient sshClient = inspectionService.connectToServer(serverConfig)) {
							String serverPath = nginxPath;

							backupAndUploadJar(sshClient, unzipPath.toFile(), serverPath, nginxFileName, serverConfig.getIp());
							deploymentSuccess = true; // 至少有一个服务器部署成功
						} catch (Exception e) {
							log.error("部署 前端 包 {} 到目标服务器 {} 失败: {}", nginxFileName, serverConfig.getIp(), e.getMessage(), e);
							throw new RuntimeException("部署前端包失败", e);
						}
					}
				}
			}

			if (deploymentSuccess) {
				log.info("[前端部署] 前端包 {} 部署成功，移动到备份目录。", zipPath.getFileName());
				moveJarToBackup(zipPath); // 部署成功后移动到 backup 文件夹

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


	private void deployJarWithRetry(Path jarFilePath) {
		int attempt = 0;
		while (attempt < MAX_RETRIES) {
			try {
				deployJar(jarFilePath);
				return; // 成功后退出循环
			} catch (Exception e) {
				attempt++;
				log.warn("deployJarWithRetry：部署 JAR 包 {} 失败，重试 {}/{} 次: {}", jarFilePath.getFileName(), attempt, MAX_RETRIES, e.getMessage());
				e.printStackTrace();
				if (attempt < MAX_RETRIES) {
					try {
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException interruptedException) {
						Thread.currentThread().interrupt();
						log.error("重试过程中线程被中断: {}", interruptedException.getMessage());
						throw new RuntimeException("部署 JAR 包重试被中断", interruptedException);
					}
				} else {
					log.error("部署 JAR 包 {} 最终失败: {}", jarFilePath.getFileName(), e.getMessage(), e);
					throw new RuntimeException("部署 JAR 包失败", e);
				}
			}
		}
	}

	public void deployJar(Path jarFilePath) {
		log.info("[JAR 部署] 开始部署 JAR 包: {}", jarFilePath.getFileName());
		if (!Files.exists(jarFilePath)) {
			log.warn("[JAR 部署] 文件 {} 不存在，跳过部署。", jarFilePath);
			return;
		}

		String jarFileName = jarFilePath.getFileName().toString();
		boolean deploymentSuccess = false; // 标记是否至少有一个服务器部署成功

		for (ServerConfig serverConfig : jarDeployConfig.getServers()) {
			for (String jarEntry : serverConfig.getJars()) {
				String[] parts = jarEntry.split(":");
				String jarName = parts[0];
				String scriptName = parts[1];

				if (jarFileName.equals(jarName)) {
					try (SSHClient sshClient = inspectionService.connectToServer(serverConfig)) {
						String serverPath = targetPath;

						backupAndUploadJar(sshClient, jarFilePath.toFile(), serverPath, jarName, serverConfig.getIp());
						//stopAndStartService(inspectionService.connectToServer(serverConfig), serverPath, jarName, scriptName);
						deploymentSuccess = true; // 至少有一个服务器部署成功
					} catch (Exception e) {
						log.error("部署 JAR 包 {} 到目标服务器 {} 失败: {}", jarFileName, serverConfig.getIp(), e.getMessage(), e);
					}
				}
			}
		}

		if (deploymentSuccess) {
			log.info("[JAR 部署] JAR 包 {} 部署成功，移动到备份目录。", jarFilePath.getFileName());
			moveJarToBackup(jarFilePath); // 部署成功后移动到 backup 文件夹
			log.info("[JAR 部署 final] 部署程序 {} 成功", jarFilePath);
		} else {
			log.error("[JAR 部署] JAR 包 {} 部署失败。", jarFilePath.getFileName());
		}
	}

	private void moveJarToBackup(Path jarFilePath) {
		if (!Files.exists(jarFilePath)) {
			log.warn("[本机备份] 文件 {} 不存在，跳过备份。", jarFilePath);
			return;
		}

		Path backup = Paths.get(scanPath, "backup");
		int attempt = 0;
		while (attempt < MAX_RETRIES) {
			try {
				if (!Files.exists(backup)) {
					Files.createDirectories(backup);
				}
				Path targetPath = backup.resolve(jarFilePath.getFileName());
				Files.move(jarFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
				log.info("已将文件 {} 移动到备份目录 {}", jarFilePath, backup);
				return; // 成功后退出循环
			} catch (IOException e) {
				attempt++;
				log.warn("移动文件 {} 到备份目录失败，重试 {}/{} 次: {}", jarFilePath, attempt, MAX_RETRIES, e.getMessage());
				if (attempt < MAX_RETRIES) {
					try {
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException interruptedException) {
						Thread.currentThread().interrupt();
						log.error("重试过程中线程被中断: {}", interruptedException.getMessage());
						return;
					}
				} else {
					log.error("移动文件 {} 到备份目录最终失败: {}", jarFilePath, e.getMessage(), e);
				}
			}
		}
	}


	private void backupAndUploadJar(SSHClient sshClient, File jarFile, String serverPath, String filename, String ip) throws IOException {
		String filePath = serverPath + filename;
		log.info("[文件上传] 准备备份并上传文件: {}", jarFile.getName());
		// 获取当前日期
		LocalDateTime currentDate = LocalDateTime.now();
		// 格式化日期和星期
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss", Locale.CHINA);
		// 输出结果
		String formattedDate = currentDate.format(formatter);

		String backupPath = filePath + "." + formattedDate;

		String command = "[ -d \"" + filePath + "\" ] || [ -f \"" + filePath + "\" ] && echo \"exists\" || echo \"not exists\"";
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
		int attempt = 0;
		while (attempt < MAX_RETRIES) {
			try (SFTPClient sftpClient = sshClient.newSFTPClient()) {
				//sftpClient.put(jarFile.getAbsolutePath(), serverPath);

				sshClient.newSCPFileTransfer().upload(jarFile.getAbsolutePath(), serverPath);

				log.info("[文件上传] 文件 {} 上传到服务器 {} 成功。", jarFile.getName(), ip);

				if (jarFile.getName().equals("dmscldpro") || jarFile.getName().equals("dmsweb") || jarFile.getName().equals("dmscldprotest")) {
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

				return; // 成功后退出循环
			} catch (IOException e) {
				attempt++;
				log.warn("上传文件 {} 到服务器失败，重试 {}/{} 次: {}", jarFile.getAbsolutePath(), attempt, MAX_RETRIES, e.getMessage());
				if (attempt < MAX_RETRIES) {
					try {
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException interruptedException) {
						Thread.currentThread().interrupt();
						log.error("重试过程中线程被中断: {}", interruptedException.getMessage());
						throw new IOException("上传文件重试被中断", interruptedException);
					}
				} else {
					log.error("上传文件 {} 到服务器最终失败: {}", jarFile.getAbsolutePath(), e.getMessage(), e);
					throw e;
				}
			}
		}
	}

	private void stopAndStartService(SSHClient sshClient, String serverPath, String fileName, String scriptName) throws IOException {
		String serviceName = fileName.replace(".jar", "");
		log.info("[服务管理] 停止服务: {}", serviceName);
		try {
			String stopCommand = "bash -c 'echo STOP && kill -9 $(jcmd -l | grep " + serviceName + " | awk \"{print \\$1}\")'";
			inspectionService.executeCommand(sshClient, stopCommand);

			// 等待服务停止
			Thread.sleep(3000);

		} catch (Exception e) {
			log.warn("停止服务 {} 失败: {}", serviceName, e.getMessage(), e);
		}

		log.info("[服务管理] 启动服务: {}", serviceName);
		try {
			String startCommand = "bash -c 'cd " + serverPath + " && ./" + scriptName + "'";
			inspectionService.executeCommand(sshClient, startCommand);
		} catch (Exception e) {
			log.error("启动服务 {} 失败: {}", serviceName, e.getMessage(), e);
			throw e;
		}
	}
}

