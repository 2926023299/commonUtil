package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.InspectionImpl.InspectionService;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
@Slf4j
public class JarDeployProcessor extends AbstractFileProcessor {

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
        Path path = Paths.get(filePath);
        log.info("[Jar包部署] 触发部署任务: {}", filePath);
        return autoScanAndDeploy(path);
    }

    public ProcessingResult autoScanAndDeploy(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".jar")) {
            return deployJar(path);
        }
        if (fileName.endsWith(".zip")) {
            return deployNginx(path);
        }
        return ProcessingResult.ignored("不支持的部署文件类型: " + path.getFileName());
    }

    private ProcessingResult deployNginx(Path zipPath) {
        log.info("[前端部署] 开始部署前端包: {}", zipPath.getFileName());
        if (!Files.exists(zipPath)) {
            return ProcessingResult.failure("前端包不存在: " + zipPath.getFileName());
        }

        Path unzipPath = null;
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            unzipPath = Files.createDirectories(
                    Paths.get(fileMonitorConfig.getDirectory(), zipPath.getFileName().toString().replace(".zip", "")));

            unzip(zipFile, unzipPath);
            deleteEmptyDirectories(unzipPath);

            String nginxFileName = unzipPath.getFileName().toString();
            DeploymentStats deploymentStats = deployArchiveTargets(unzipPath.toFile(), nginxFileName, nginxPath);

            if (deploymentStats.getMatchedTargets() == 0) {
                log.info("[前端部署] 前端包 {} 不在服务器的部署目标，记为忽略。", nginxFileName);
                return ProcessingResult.ignored("前端包未匹配部署目标: " + nginxFileName);
            }

            if (deploymentStats.hasFailure()) {
                return ProcessingResult.failure(buildFailureMessage("前端包", nginxFileName, deploymentStats));
            }

            log.info("[前端部署] 前端包 {} 部署成功", zipPath.getFileName());
            return ProcessingResult.success("前端包部署成功", deploymentStats.getSuccessTargets());
        } catch (IOException e) {
            log.error("无法打开 ZIP 文件 {}: {}", zipPath, e.getMessage(), e);
            return ProcessingResult.failure("无法打开 ZIP 文件", e);
        } finally {
            cleanupDirectory(unzipPath);
        }
    }

    private ProcessingResult deployJar(Path jarFilePath) {
        log.info("[JAR 部署] 开始部署 JAR 包: {}", jarFilePath.getFileName());
        if (!Files.exists(jarFilePath)) {
            return ProcessingResult.failure("JAR包不存在: " + jarFilePath.getFileName());
        }

        String jarFileName = jarFilePath.getFileName().toString();
        DeploymentStats deploymentStats = deployArchiveTargets(jarFilePath.toFile(), jarFileName, targetPath);

        if (deploymentStats.getMatchedTargets() == 0) {
            log.info("[JAR 部署] JAR 包 {} 不在服务器的部署目标，记为忽略。", jarFileName);
            return ProcessingResult.ignored("JAR包未匹配部署目标: " + jarFileName);
        }

        if (deploymentStats.hasFailure()) {
            return ProcessingResult.failure(buildFailureMessage("JAR包", jarFileName, deploymentStats));
        }

        log.info("[JAR 部署] JAR 包 {} 部署成功", jarFilePath.getFileName());
        return ProcessingResult.success("Jar包部署成功", deploymentStats.getSuccessTargets());
    }

    private DeploymentStats deployArchiveTargets(File sourceFile, String targetName, String remoteBasePath) {
        DeploymentStats deploymentStats = new DeploymentStats();

        for (ServerConfig serverConfig : jarDeployConfig.getServers()) {
            for (String jarEntry : serverConfig.getJars()) {
                String[] parts = jarEntry.split(":");
                String configuredName = parts[0];

                if (!targetName.equals(configuredName)) {
                    continue;
                }

                deploymentStats.incrementMatchedTargets();
                try (SSHClient sshClient = inspectionService.connectToServer(serverConfig)) {
                    backupAndUploadJar(sshClient, sourceFile, remoteBasePath, configuredName, serverConfig.getIp());
                    deploymentStats.incrementSuccessTargets();
                } catch (Exception e) {
                    log.error("部署文件 {} 到目标服务器 {} 失败: {}", targetName, serverConfig.getIp(), e.getMessage(), e);
                    deploymentStats.addFailedServer(serverConfig.getIp());
                }
            }
        }

        return deploymentStats;
    }

    private String buildFailureMessage(String fileType, String fileName, DeploymentStats deploymentStats) {
        return String.format("%s部署失败: %s，命中%d个目标，成功%d个，失败服务器=%s",
                fileType,
                fileName,
                deploymentStats.getMatchedTargets(),
                deploymentStats.getSuccessTargets(),
                deploymentStats.getFailedServers());
    }

    private void unzip(ZipFile zipFile, Path unzipPath) throws IOException {
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String entryName = entry.getName();
            int firstSeparatorIndex = entryName.indexOf('/');
            if (firstSeparatorIndex != -1 && firstSeparatorIndex < entryName.length() - 1) {
                entryName = entryName.substring(firstSeparatorIndex + 1);
            }
            if (entryName.isEmpty()) {
                continue;
            }

            Path entryPath = unzipPath.resolve(entryName);
            if (entry.isDirectory()) {
                Files.createDirectories(entryPath);
                continue;
            }

            if (entryPath.getParent() != null) {
                Files.createDirectories(entryPath.getParent());
            }
            try (InputStream inputStream = zipFile.getInputStream(entry);
                 OutputStream outputStream = Files.newOutputStream(entryPath)) {
                IOUtils.copy(inputStream, outputStream);
            }
        }
    }

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
                            Files.deleteIfExists(dir);
                            log.warn("目录 {} 为空，直接删除", dir);
                        }
                    } catch (IOException e) {
                        log.error("删除空目录失败: {}", dir, e);
                    }
                });
    }

    private void cleanupDirectory(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException e) {
                            log.error("删除失败: {}", current, e);
                        }
                    });
        } catch (IOException e) {
            log.error("删除解压后的文件失败: {}", e.getMessage(), e);
        }
    }

    private void backupAndUploadJar(SSHClient sshClient, File jarFile, String serverPath, String filename, String ip)
            throws IOException {
        String filePath = serverPath + filename;
        log.info("[文件上传] 准备备份并上传文件: {}", jarFile.getName());

        LocalDateTime currentDate = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss", Locale.CHINA);
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

        try {
            sshClient.newSCPFileTransfer().upload(jarFile.getAbsolutePath(), serverPath);
            log.info("[文件上传] 文件 {} 上传到服务器 {} 成功。", jarFile.getName(), ip);

            if (jarFile.getName().equals("dmscldpro") || jarFile.getName().equals("dmsweb")
                    || jarFile.getName().equals("dmscldprotest") || jarFile.getName().equals("dmscldtest")
                    || jarFile.getName().equals("dmscloud") || jarFile.getName().equals("dmscldgz")) {
                copyRemotePath(sshClient, backupPath + "/config.js", filePath);
            }

            if (jarFile.getName().equals("svg-ui")) {
                copyRemotePath(sshClient, backupPath + "/config", filePath);
            }

            if (jarFile.getName().equals("topo-ui")) {
                copyRemotePath(sshClient, backupPath + "/config", filePath);
            }
        } catch (IOException e) {
            log.warn("上传文件 {} 到服务器失败，{}", jarFile.getAbsolutePath(), e.getMessage());
            throw e;
        }
    }

    private void copyRemotePath(SSHClient sshClient, String source, String target) {
        try {
            inspectionService.executeCommand(sshClient, "cp -r " + source + " " + target);
        } catch (Exception e) {
            log.warn("复制远程文件失败: {}", e.getMessage(), e);
        }
    }

    private static class DeploymentStats {
        private int matchedTargets;
        private int successTargets;
        private final List<String> failedServers = new ArrayList<String>();

        int getMatchedTargets() {
            return matchedTargets;
        }

        int getSuccessTargets() {
            return successTargets;
        }

        List<String> getFailedServers() {
            return failedServers;
        }

        void incrementMatchedTargets() {
            matchedTargets++;
        }

        void incrementSuccessTargets() {
            successTargets++;
        }

        void addFailedServer(String failedServer) {
            failedServers.add(failedServer);
        }

        boolean hasFailure() {
            return !failedServers.isEmpty();
        }
    }
}
