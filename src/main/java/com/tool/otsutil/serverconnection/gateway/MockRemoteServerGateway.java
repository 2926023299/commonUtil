package com.tool.otsutil.serverconnection.gateway;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.serverconnection.model.view.RemoteBreadcrumbItemView;
import com.tool.otsutil.serverconnection.model.view.RemoteFileEntryView;
import com.tool.otsutil.serverconnection.model.view.RemoteFileListView;
import com.tool.otsutil.serverconnection.service.ServerCatalogService;
import com.tool.otsutil.serverconnection.util.PosixPathUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MockRemoteServerGateway implements RemoteServerGateway {

    @Override
    public ServerConnectionHandle openConnection(ServerConfig serverConfig) throws IOException {
        Path rootPath = Paths.get(System.getProperty("java.io.tmpdir"), "server-connection-mock", serverConfig.getIp().replace('.', '_'));
        Files.createDirectories(rootPath);
        return new MockServerConnectionHandle(ServerCatalogService.buildServerKey(serverConfig), rootPath);
    }

    @Override
    public String resolveHomeDirectory(ServerConnectionHandle handle) {
        return "/";
    }

    @Override
    public String canonicalizePath(ServerConnectionHandle handle, String basePath, String requestedPath) throws IOException {
        String normalized = PosixPathUtils.normalize(basePath, requestedPath);
        Path localPath = toLocalPath(handle, normalized);
        if (!Files.exists(localPath)) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "远程路径不存在");
        }
        return normalized;
    }

    @Override
    public ServerShell openShell(ServerConnectionHandle handle, String initialPath) throws IOException {
        return openShell(handle, initialPath, "UTF-8");
    }

    @Override
    public ServerShell openShell(ServerConnectionHandle handle, String initialPath, String charset) throws IOException {
        return new MockServerShell(initialPath);
    }

    @Override
    public RemoteFileListView listFiles(ServerConnectionHandle handle, String basePath, String requestedPath) throws IOException {
        String cwd = canonicalizePath(handle, basePath, requestedPath);
        Path localPath = toLocalPath(handle, cwd);
        if (!Files.isDirectory(localPath)) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "目标不是目录");
        }

        RemoteFileListView view = new RemoteFileListView();
        view.setCwd(cwd);
        view.setParentPath(PosixPathUtils.parent(cwd));
        view.setBreadcrumbs(buildBreadcrumbs(cwd));

        List<RemoteFileEntryView> entries = new ArrayList<RemoteFileEntryView>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localPath)) {
            for (Path current : stream) {
                RemoteFileEntryView entry = new RemoteFileEntryView();
                entry.setName(current.getFileName().toString());
                entry.setPath(PosixPathUtils.normalize(cwd, entry.getName()));
                entry.setDirectory(Files.isDirectory(current));
                entry.setSize(entry.isDirectory() ? 0L : Files.size(current));
                entry.setLastModified(Files.getLastModifiedTime(current).toMillis());
                entries.add(entry);
            }
        }

        entries.sort(new Comparator<RemoteFileEntryView>() {
            @Override
            public int compare(RemoteFileEntryView left, RemoteFileEntryView right) {
                if (left.isDirectory() == right.isDirectory()) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
                return left.isDirectory() ? -1 : 1;
            }
        });

        view.setEntries(entries);
        return view;
    }

    @Override
    public String createDirectory(ServerConnectionHandle handle, String basePath, String parentPath, String name) throws IOException {
        if (name == null || name.trim().isEmpty() || name.contains("/")) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "目录名不合法");
        }
        String parent = canonicalizePath(handle, basePath, parentPath);
        String target = PosixPathUtils.normalize(parent, name.trim());
        Files.createDirectories(toLocalPath(handle, target));
        return target;
    }

    @Override
    public String rename(ServerConnectionHandle handle, String basePath, String fromPath, String toPath) throws IOException {
        String source = canonicalizePath(handle, basePath, fromPath);
        String targetBase = basePath;
        if (!toPath.startsWith("/") && !toPath.contains("/")) {
            String parent = PosixPathUtils.parent(source);
            targetBase = parent == null ? "/" : parent;
        }
        String target = PosixPathUtils.normalize(targetBase, toPath);
        Files.move(toLocalPath(handle, source), toLocalPath(handle, target));
        return target;
    }

    @Override
    public void delete(ServerConnectionHandle handle, String basePath, String path, boolean recursive) throws IOException {
        String target = canonicalizePath(handle, basePath, path);
        Path localPath = toLocalPath(handle, target);
        if (Files.isDirectory(localPath)) {
            if (!recursive) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(localPath)) {
                    if (stream.iterator().hasNext()) {
                        throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "只能删除空目录");
                    }
                }
                Files.delete(localPath);
                return;
            }
            Files.walk(localPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
            return;
        }
        Files.delete(localPath);
    }

    @Override
    public DownloadedRemoteFile downloadFile(ServerConnectionHandle handle, String basePath, String path) throws IOException {
        String target = canonicalizePath(handle, basePath, path);
        Path localPath = toLocalPath(handle, target);
        if (Files.isDirectory(localPath)) {
            DownloadedRemoteFile downloadedRemoteFile = new DownloadedRemoteFile();
            downloadedRemoteFile.setFileName(localPath.getFileName().toString() + ".zip");
            downloadedRemoteFile.setContentType("application/zip");
            downloadedRemoteFile.setContent(zipDirectory(localPath));
            return downloadedRemoteFile;
        }

        DownloadedRemoteFile downloadedRemoteFile = new DownloadedRemoteFile();
        downloadedRemoteFile.setFileName(localPath.getFileName().toString());
        downloadedRemoteFile.setContentType("application/octet-stream");
        downloadedRemoteFile.setContent(Files.readAllBytes(localPath));
        return downloadedRemoteFile;
    }

    @Override
    public DownloadedRemoteFile streamFile(ServerConnectionHandle handle, String basePath, String path) throws IOException {
        String target = canonicalizePath(handle, basePath, path);
        Path localPath = toLocalPath(handle, target);
        if (Files.isDirectory(localPath)) {
            DownloadedRemoteFile result = downloadFile(handle, basePath, path);
            result.setContentStream(new java.io.ByteArrayInputStream(result.getContent()));
            result.setContentLength(result.getContent().length);
            return result;
        }

        DownloadedRemoteFile result = new DownloadedRemoteFile();
        result.setFileName(localPath.getFileName().toString());
        result.setContentType("application/octet-stream");
        result.setContentLength(Files.size(localPath));
        result.setContentStream(Files.newInputStream(localPath));
        return result;
    }

    @Override
    public void uploadFiles(ServerConnectionHandle handle, String basePath, String targetPath, MultipartFile[] files) throws IOException {
        String targetDirectory = canonicalizePath(handle, basePath, targetPath);
        Path localDirectory = toLocalPath(handle, targetDirectory);
        if (!Files.isDirectory(localDirectory)) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "上传目标必须是目录");
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            Path localFile = localDirectory.resolve(file.getOriginalFilename());
            file.transferTo(localFile.toFile());
        }
    }

    private Path toLocalPath(ServerConnectionHandle handle, String virtualPath) {
        MockServerConnectionHandle mockHandle = (MockServerConnectionHandle) handle;
        if ("/".equals(virtualPath)) {
            return mockHandle.getRootPath();
        }
        return mockHandle.getRootPath().resolve(virtualPath.substring(1).replace('/', java.io.File.separatorChar));
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

    private byte[] zipDirectory(Path directory) throws IOException {
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(outputStream);
        try {
            java.nio.file.SimpleFileVisitor<Path> visitor = new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    String relativePath = directory.relativize(file).toString().replace('\\', '/');
                    zipOutputStream.putNextEntry(new java.util.zip.ZipEntry(relativePath));
                    zipOutputStream.write(Files.readAllBytes(file));
                    zipOutputStream.closeEntry();
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            };
            java.nio.file.Files.walkFileTree(directory, visitor);
        } finally {
            zipOutputStream.close();
        }
        return outputStream.toByteArray();
    }
}
