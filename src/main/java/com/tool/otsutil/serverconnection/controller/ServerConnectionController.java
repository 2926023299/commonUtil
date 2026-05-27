package com.tool.otsutil.serverconnection.controller;

import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.serverconnection.gateway.DownloadedRemoteFile;
import com.tool.otsutil.serverconnection.model.request.CreateDirectoryRequest;
import com.tool.otsutil.serverconnection.model.request.DeleteRemoteFileRequest;
import com.tool.otsutil.serverconnection.model.request.OpenTerminalSessionRequest;
import com.tool.otsutil.serverconnection.model.request.RenameRemoteFileRequest;
import com.tool.otsutil.serverconnection.model.view.RemoteFileListView;
import com.tool.otsutil.serverconnection.model.view.ServerConnectionView;
import com.tool.otsutil.serverconnection.model.view.TerminalSessionView;
import com.tool.otsutil.serverconnection.service.TerminalSessionManager;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/server-connections")
public class ServerConnectionController {

    private final TerminalSessionManager terminalSessionManager;

    public ServerConnectionController(TerminalSessionManager terminalSessionManager) {
        this.terminalSessionManager = terminalSessionManager;
    }

    @GetMapping("/servers")
    public ResponseResult<List<ServerConnectionView>> listServers() {
        return ResponseResult.okResult(terminalSessionManager.listServers());
    }

    @PostMapping("/sessions")
    public ResponseResult<TerminalSessionView> openSession(@RequestBody OpenTerminalSessionRequest request) throws IOException {
        return ResponseResult.okResult(terminalSessionManager.openSession(request));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseResult<Void> closeSession(@PathVariable String sessionId) {
        terminalSessionManager.closeSession(sessionId);
        return ResponseResult.okResult(null);
    }

    @GetMapping("/sessions/{sessionId}/cwd")
    public ResponseResult<TerminalSessionView> getSession(@PathVariable String sessionId) {
        return ResponseResult.okResult(terminalSessionManager.getSession(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/files")
    public ResponseResult<RemoteFileListView> listFiles(@PathVariable String sessionId,
                                                        @RequestParam(required = false) String path) throws IOException {
        return ResponseResult.okResult(terminalSessionManager.listFiles(sessionId, path));
    }

    @PostMapping("/sessions/{sessionId}/directories")
    public ResponseResult<String> createDirectory(@PathVariable String sessionId,
                                                  @RequestBody CreateDirectoryRequest request) throws IOException {
        return ResponseResult.okResult(terminalSessionManager.createDirectory(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/files/rename")
    public ResponseResult<String> rename(@PathVariable String sessionId,
                                         @RequestBody RenameRemoteFileRequest request) throws IOException {
        return ResponseResult.okResult(terminalSessionManager.rename(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/files/delete")
    public ResponseResult<Void> delete(@PathVariable String sessionId,
                                       @RequestBody DeleteRemoteFileRequest request) throws IOException {
        terminalSessionManager.delete(sessionId, request);
        return ResponseResult.okResult(null);
    }

    @PostMapping("/sessions/{sessionId}/upload")
    public ResponseResult<Void> upload(@PathVariable String sessionId,
                                       @RequestParam(required = false) String path,
                                       @RequestParam("files") MultipartFile[] files) throws IOException {
        terminalSessionManager.uploadFiles(sessionId, path, files);
        return ResponseResult.okResult(null);
    }

    @GetMapping("/sessions/{sessionId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String sessionId,
                                                        @RequestParam String path) throws IOException {
        DownloadedRemoteFile file = terminalSessionManager.streamFile(sessionId, path);
        String encodedFileName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8.name()).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFileName() + "\"; filename*=UTF-8''" + encodedFileName);
        if (file.getContentLength() >= 0) {
            headers.setContentLength(file.getContentLength());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(file.getContentStream()));
    }
}
