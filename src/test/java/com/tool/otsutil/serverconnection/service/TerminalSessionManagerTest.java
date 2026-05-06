package com.tool.otsutil.serverconnection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.serverconnection.config.ServerConnectionProperties;
import com.tool.otsutil.serverconnection.gateway.DownloadedRemoteFile;
import com.tool.otsutil.serverconnection.gateway.MockRemoteServerGateway;
import com.tool.otsutil.serverconnection.model.request.CreateDirectoryRequest;
import com.tool.otsutil.serverconnection.model.request.DeleteRemoteFileRequest;
import com.tool.otsutil.serverconnection.model.request.OpenTerminalSessionRequest;
import com.tool.otsutil.serverconnection.model.request.RenameRemoteFileRequest;
import com.tool.otsutil.serverconnection.model.view.RemoteFileListView;
import com.tool.otsutil.serverconnection.model.view.TerminalClientMessage;
import com.tool.otsutil.serverconnection.model.view.TerminalSessionView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

class TerminalSessionManagerTest {

    @Test
    void shouldManageMockSessionAndRemoteFiles() throws Exception {
        InspectionConfig inspectionConfig = new InspectionConfig();
        inspectionConfig.setServers(Collections.singletonList(
                new ServerConfig("10.0.0.1", 22, "root", "secret", Collections.singletonList("app.jar:start.sh"))
        ));

        ServerCatalogService catalogService = new ServerCatalogService(inspectionConfig);
        ServerConnectionProperties properties = new ServerConnectionProperties();
        TerminalSessionManager manager = new TerminalSessionManager(
                catalogService,
                new MockRemoteServerGateway(),
                properties,
                new ObjectMapper()
        );

        OpenTerminalSessionRequest request = new OpenTerminalSessionRequest();
        request.setServerKey("10.0.0.1:22:root");
        TerminalSessionView session = manager.openSession(request);

        CreateDirectoryRequest createDirectoryRequest = new CreateDirectoryRequest();
        createDirectoryRequest.setPath("/");
        createDirectoryRequest.setName("workspace");
        manager.createDirectory(session.getSessionId(), createDirectoryRequest);

        TerminalClientMessage cdMessage = new TerminalClientMessage();
        cdMessage.setType("input");
        cdMessage.setData("cd /workspace\n");
        manager.handleTerminalMessage(session.getSessionId(), cdMessage);

        Assertions.assertEquals("/workspace", manager.getSession(session.getSessionId()).getCwd());

        createDirectoryRequest.setPath("/workspace");
        createDirectoryRequest.setName("release");
        manager.createDirectory(session.getSessionId(), createDirectoryRequest);

        MockMultipartFile multipartFile = new MockMultipartFile(
                "files",
                "demo.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        manager.uploadFiles(session.getSessionId(), "/workspace/release", new MockMultipartFile[]{multipartFile});

        RemoteFileListView listView = manager.listFiles(session.getSessionId(), "/workspace/release");
        Assertions.assertEquals("/workspace/release", listView.getCwd());
        Assertions.assertEquals(1, listView.getEntries().size());
        Assertions.assertEquals("demo.txt", listView.getEntries().get(0).getName());

        RenameRemoteFileRequest renameRequest = new RenameRemoteFileRequest();
        renameRequest.setFromPath("/workspace/release/demo.txt");
        renameRequest.setToPath("demo-renamed.txt");
        String renamedPath = manager.rename(session.getSessionId(), renameRequest);
        Assertions.assertTrue(renamedPath.endsWith("demo-renamed.txt"));

        DownloadedRemoteFile downloadedFile = manager.downloadFile(session.getSessionId(), "/workspace/release/demo-renamed.txt");
        Assertions.assertEquals("demo-renamed.txt", downloadedFile.getFileName());
        Assertions.assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), downloadedFile.getContent());

        Assertions.assertThrows(CustomException.class, () -> manager.delete(session.getSessionId(), createDeleteRequest("/workspace/release")));

        DownloadedRemoteFile downloadedDirectory = manager.downloadFile(session.getSessionId(), "/workspace/release");
        Assertions.assertEquals("release.zip", downloadedDirectory.getFileName());
        Assertions.assertTrue(downloadedDirectory.getContent().length > 0);

        manager.delete(session.getSessionId(), createDeleteRequest("/workspace/release/demo-renamed.txt"));
        manager.delete(session.getSessionId(), createDeleteRequest("/workspace/release", true));
    }

    @Test
    void shouldNotCleanupIdleSessionsWhenIdleTimeoutIsDisabled() throws Exception {
        InspectionConfig inspectionConfig = new InspectionConfig();
        inspectionConfig.setServers(Collections.singletonList(
                new ServerConfig("10.0.0.2", 22, "root", "secret", Collections.singletonList("app.jar:start.sh"))
        ));

        ServerCatalogService catalogService = new ServerCatalogService(inspectionConfig);
        ServerConnectionProperties properties = new ServerConnectionProperties();
        properties.setIdleTimeoutMinutes(0);
        TerminalSessionManager manager = new TerminalSessionManager(
                catalogService,
                new MockRemoteServerGateway(),
                properties,
                new ObjectMapper()
        );

        OpenTerminalSessionRequest request = new OpenTerminalSessionRequest();
        request.setServerKey("10.0.0.2:22:root");
        TerminalSessionView session = manager.openSession(request);

        Thread.sleep(5L);
        manager.cleanupIdleSessions();

        Assertions.assertEquals(session.getSessionId(), manager.getSession(session.getSessionId()).getSessionId());
    }

    private DeleteRemoteFileRequest createDeleteRequest(String path) {
        return createDeleteRequest(path, false);
    }

    private DeleteRemoteFileRequest createDeleteRequest(String path, boolean recursive) {
        DeleteRemoteFileRequest request = new DeleteRemoteFileRequest();
        request.setPath(path);
        request.setRecursive(recursive);
        return request;
    }
}
