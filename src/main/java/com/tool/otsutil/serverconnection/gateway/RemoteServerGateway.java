package com.tool.otsutil.serverconnection.gateway;

import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.serverconnection.model.view.RemoteFileListView;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface RemoteServerGateway {

    ServerConnectionHandle openConnection(ServerConfig serverConfig) throws IOException;

    String resolveHomeDirectory(ServerConnectionHandle handle) throws IOException;

    String canonicalizePath(ServerConnectionHandle handle, String basePath, String requestedPath) throws IOException;

    ServerShell openShell(ServerConnectionHandle handle, String initialPath) throws IOException;

    ServerShell openShell(ServerConnectionHandle handle, String initialPath, String charset) throws IOException;

    RemoteFileListView listFiles(ServerConnectionHandle handle, String basePath, String requestedPath) throws IOException;

    String createDirectory(ServerConnectionHandle handle, String basePath, String parentPath, String name) throws IOException;

    String rename(ServerConnectionHandle handle, String basePath, String fromPath, String toPath) throws IOException;

    void delete(ServerConnectionHandle handle, String basePath, String path, boolean recursive) throws IOException;

    DownloadedRemoteFile downloadFile(ServerConnectionHandle handle, String basePath, String path) throws IOException;

    DownloadedRemoteFile streamFile(ServerConnectionHandle handle, String basePath, String path) throws IOException;

    void uploadFiles(ServerConnectionHandle handle, String basePath, String targetPath, MultipartFile[] files) throws IOException;
}
