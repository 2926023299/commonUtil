package com.tool.otsutil.serverconnection.gateway;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.serverconnection.config.ServerConnectionProperties;
import com.tool.otsutil.serverconnection.model.view.RemoteBreadcrumbItemView;
import com.tool.otsutil.serverconnection.model.view.RemoteFileEntryView;
import com.tool.otsutil.serverconnection.model.view.RemoteFileListView;
import com.tool.otsutil.serverconnection.util.PosixPathUtils;
import com.tool.otsutil.service.InspectionImpl.InspectionService;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SshjRemoteServerGateway implements RemoteServerGateway {

    private final InspectionService inspectionService;
    private final ServerConnectionProperties properties;

    public SshjRemoteServerGateway(InspectionService inspectionService, ServerConnectionProperties properties) {
        this.inspectionService = inspectionService;
        this.properties = properties;
    }

    @Override
    public ServerConnectionHandle openConnection(ServerConfig serverConfig) throws IOException {
        SSHClient sshClient = inspectionService.connectToServer(serverConfig);
        if (properties.getSshKeepaliveIntervalSeconds() > 0) {
            sshClient.getConnection().getKeepAlive().setKeepAliveInterval(properties.getSshKeepaliveIntervalSeconds());
        }
        return new SshjServerConnectionHandle(buildServerKey(serverConfig), sshClient);
    }

    @Override
    public String resolveHomeDirectory(ServerConnectionHandle handle) throws IOException {
        try (SFTPClient sftpClient = getClient(handle)) {
            return sftpClient.canonicalize(".");
        }
    }

    @Override
    public String canonicalizePath(ServerConnectionHandle handle, String basePath, String requestedPath) throws IOException {
        String normalizedPath = PosixPathUtils.normalize(basePath, requestedPath);
        try (SFTPClient sftpClient = getClient(handle)) {
            FileAttributes attributes = sftpClient.statExistence(normalizedPath);
            if (attributes == null) {
                throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "远程路径不存在");
            }
            return sftpClient.canonicalize(normalizedPath);
        }
    }

    @Override
    public ServerShell openShell(ServerConnectionHandle handle, String initialPath) throws IOException {
        Session session = getHandle(handle).getSshClient().startSession();
        Map<PTYMode, Integer> ptyModes = new EnumMap<PTYMode, Integer>(PTYMode.class);
        ptyModes.put(PTYMode.ECHO, 0);
        session.allocatePTY("xterm", 120, 32, 960, 512, ptyModes);
        Session.Shell shell = session.startShell();
        return new SshjServerShell(session, shell, initialPath);
    }

    @Override
    public RemoteFileListView listFiles(ServerConnectionHandle handle, String basePath, String requestedPath) throws IOException {
        try (SFTPClient sftpClient = getClient(handle)) {
            String cwd = resolveExistingPath(sftpClient, basePath, requestedPath);
            List<RemoteResourceInfo> resources = sftpClient.ls(cwd);
            resources.sort(new Comparator<RemoteResourceInfo>() {
                @Override
                public int compare(RemoteResourceInfo left, RemoteResourceInfo right) {
                    if (left.isDirectory() == right.isDirectory()) {
                        return left.getName().compareToIgnoreCase(right.getName());
                    }
                    return left.isDirectory() ? -1 : 1;
                }
            });

            RemoteFileListView view = new RemoteFileListView();
            view.setCwd(cwd);
            view.setParentPath(PosixPathUtils.parent(cwd));
            view.setBreadcrumbs(buildBreadcrumbs(cwd));

            for (RemoteResourceInfo resource : resources) {
                String name = resource.getName();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }

                RemoteFileEntryView entry = new RemoteFileEntryView();
                entry.setName(name);
                entry.setPath(resource.getPath());
                entry.setDirectory(resource.isDirectory());
                entry.setLastModified(resource.getAttributes().getMtime() * 1000L);
                entry.setSize(resource.isDirectory() ? 0L : resource.getAttributes().getSize());
                view.getEntries().add(entry);
            }
            return view;
        }
    }

    @Override
    public String createDirectory(ServerConnectionHandle handle, String basePath, String parentPath, String name) throws IOException {
        if (name == null || name.trim().isEmpty() || name.contains("/")) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "目录名不合法");
        }

        try (SFTPClient sftpClient = getClient(handle)) {
            String canonicalParent = resolveExistingPath(sftpClient, basePath, parentPath);
            String targetPath = PosixPathUtils.normalize(canonicalParent, name.trim());
            sftpClient.mkdir(targetPath);
            return targetPath;
        }
    }

    @Override
    public String rename(ServerConnectionHandle handle, String basePath, String fromPath, String toPath) throws IOException {
        if (toPath == null || toPath.trim().isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "目标路径不能为空");
        }

        try (SFTPClient sftpClient = getClient(handle)) {
            String sourcePath = resolveExistingPath(sftpClient, basePath, fromPath);
            String targetBase = basePath;
            if (!toPath.startsWith("/") && !toPath.contains("/")) {
                String parent = PosixPathUtils.parent(sourcePath);
                targetBase = parent == null ? "/" : parent;
            }
            String targetPath = PosixPathUtils.normalize(targetBase, toPath);
            sftpClient.rename(sourcePath, targetPath);
            return targetPath;
        }
    }

    @Override
    public void delete(ServerConnectionHandle handle, String basePath, String path, boolean recursive) throws IOException {
        try (SFTPClient sftpClient = getClient(handle)) {
            String targetPath = resolveExistingPath(sftpClient, basePath, path);
            FileAttributes attributes = sftpClient.stat(targetPath);
            if (attributes.getType() == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
                if (!recursive) {
                    List<RemoteResourceInfo> children = sftpClient.ls(targetPath);
                    int effectiveChildren = 0;
                    for (RemoteResourceInfo child : children) {
                        if (!".".equals(child.getName()) && !"..".equals(child.getName())) {
                            effectiveChildren++;
                        }
                    }
                    if (effectiveChildren > 0) {
                        throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "只能删除空目录");
                    }
                    sftpClient.rmdir(targetPath);
                    return;
                }
                deleteDirectoryRecursively(sftpClient, targetPath);
                return;
            }

            sftpClient.rm(targetPath);
        }
    }

    @Override
    public DownloadedRemoteFile downloadFile(ServerConnectionHandle handle, String basePath, String path) throws IOException {
        try (SFTPClient sftpClient = getClient(handle)) {
            String targetPath = resolveExistingPath(sftpClient, basePath, path);
            FileAttributes attributes = sftpClient.stat(targetPath);
            if (attributes.getType() == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
                Path tempDirectory = Files.createTempDirectory("server-connection-dir-download-");
                try {
                    downloadDirectoryRecursively(sftpClient, targetPath, tempDirectory);
                    DownloadedRemoteFile downloadedFile = new DownloadedRemoteFile();
                    downloadedFile.setFileName(extractName(targetPath) + ".zip");
                    downloadedFile.setContentType("application/zip");
                    downloadedFile.setContent(zipDirectory(tempDirectory));
                    return downloadedFile;
                } finally {
                    deleteLocalDirectory(tempDirectory);
                }
            }

            Path temporaryFile = Files.createTempFile("server-connection-download-", "-" + extractName(targetPath));
            try {
                sftpClient.get(targetPath, temporaryFile.toString());
                DownloadedRemoteFile downloadedFile = new DownloadedRemoteFile();
                downloadedFile.setFileName(extractName(targetPath));
                downloadedFile.setContentType("application/octet-stream");
                downloadedFile.setContent(Files.readAllBytes(temporaryFile));
                return downloadedFile;
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    @Override
    public void uploadFiles(ServerConnectionHandle handle, String basePath, String targetPath, MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) {
            throw new CustomException(AppHttpCodeEnum.PARAM_REQUIRE, "缺少上传文件");
        }

        try (SFTPClient sftpClient = getClient(handle)) {
            String targetDirectory = resolveExistingPath(sftpClient, basePath, targetPath);
            FileAttributes attributes = sftpClient.stat(targetDirectory);
            if (attributes.getType() != net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
                throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "上传目标必须是目录");
            }

            for (MultipartFile multipartFile : files) {
                if (multipartFile == null || multipartFile.isEmpty()) {
                    continue;
                }

                Path temporaryFile = Files.createTempFile("server-connection-upload-", "-" + multipartFile.getOriginalFilename());
                try {
                    multipartFile.transferTo(temporaryFile.toFile());
                    String remotePath = PosixPathUtils.normalize(targetDirectory, multipartFile.getOriginalFilename());
                    sftpClient.put(temporaryFile.toString(), remotePath);
                } catch (Exception exception) {
                    throw new IOException("上传文件失败: " + multipartFile.getOriginalFilename(), exception);
                } finally {
                    Files.deleteIfExists(temporaryFile);
                }
            }
        }
    }

    private SFTPClient getClient(ServerConnectionHandle handle) throws IOException {
        return getHandle(handle).getSshClient().newSFTPClient();
    }

    private SshjServerConnectionHandle getHandle(ServerConnectionHandle handle) {
        if (!(handle instanceof SshjServerConnectionHandle)) {
            throw new IllegalArgumentException("Unsupported handle implementation");
        }
        return (SshjServerConnectionHandle) handle;
    }

    private String resolveExistingPath(SFTPClient sftpClient, String basePath, String requestedPath) throws IOException {
        String normalizedPath = PosixPathUtils.normalize(basePath, requestedPath);
        FileAttributes attributes = sftpClient.statExistence(normalizedPath);
        if (attributes == null) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "远程路径不存在");
        }
        return sftpClient.canonicalize(normalizedPath);
    }

    private List<RemoteBreadcrumbItemView> buildBreadcrumbs(String cwd) {
        List<RemoteBreadcrumbItemView> breadcrumbs = new ArrayList<RemoteBreadcrumbItemView>();
        RemoteBreadcrumbItemView root = new RemoteBreadcrumbItemView();
        root.setLabel("/");
        root.setPath("/");
        breadcrumbs.add(root);

        if ("/".equals(cwd)) {
            return breadcrumbs;
        }

        StringBuilder currentPath = new StringBuilder();
        for (String part : cwd.substring(1).split("/")) {
            currentPath.append('/').append(part);
            RemoteBreadcrumbItemView item = new RemoteBreadcrumbItemView();
            item.setLabel(part);
            item.setPath(currentPath.toString());
            breadcrumbs.add(item);
        }
        return breadcrumbs;
    }

    private String extractName(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "download.bin";
        }
        int separatorIndex = path.lastIndexOf('/');
        return separatorIndex >= 0 ? path.substring(separatorIndex + 1) : path;
    }

    private String buildServerKey(ServerConfig serverConfig) {
        return serverConfig.getIp() + ":" + serverConfig.getPort() + ":" + serverConfig.getUsername();
    }

    private void deleteDirectoryRecursively(SFTPClient sftpClient, String directory) throws IOException {
        List<RemoteResourceInfo> children = sftpClient.ls(directory);
        for (RemoteResourceInfo child : children) {
            String name = child.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }

            if (child.isDirectory()) {
                deleteDirectoryRecursively(sftpClient, child.getPath());
            } else {
                sftpClient.rm(child.getPath());
            }
        }
        sftpClient.rmdir(directory);
    }

    private void downloadDirectoryRecursively(SFTPClient sftpClient, String remotePath, Path localDirectory) throws IOException {
        Files.createDirectories(localDirectory);
        for (RemoteResourceInfo child : sftpClient.ls(remotePath)) {
            String name = child.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }

            Path localTarget = localDirectory.resolve(name);
            if (child.isDirectory()) {
                downloadDirectoryRecursively(sftpClient, child.getPath(), localTarget);
            } else {
                Files.createDirectories(localTarget.getParent());
                sftpClient.get(child.getPath(), localTarget.toString());
            }
        }
    }

    private byte[] zipDirectory(Path directory) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        try {
            Files.walk(directory)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String relativePath = directory.relativize(path).toString().replace('\\', '/');
                        try {
                            zipOutputStream.putNextEntry(new ZipEntry(relativePath));
                            zipOutputStream.write(Files.readAllBytes(path));
                            zipOutputStream.closeEntry();
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException) {
                throw (IOException) exception.getCause();
            }
            throw exception;
        } finally {
            zipOutputStream.close();
        }
        return outputStream.toByteArray();
    }

    private void deleteLocalDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        Files.walk(directory)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
    }
}
