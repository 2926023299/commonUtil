package com.tool.otsutil.serverconnection.gateway;

import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.serverconnection.config.ServerConnectionProperties;
import com.tool.otsutil.service.InspectionImpl.InspectionService;
import net.schmizz.keepalive.KeepAlive;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.Connection;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Map;

class SshjRemoteServerGatewayTest {

    @Test
    void shouldOpenShellWithEchoDisabledBeforeBootstrap() throws Exception {
        SSHClient sshClient = Mockito.mock(SSHClient.class);
        Session session = Mockito.mock(Session.class);
        Session.Shell shell = Mockito.mock(Session.Shell.class);
        Mockito.when(sshClient.startSession()).thenReturn(session);
        Mockito.when(session.startShell()).thenReturn(shell);

        SshjRemoteServerGateway gateway = new SshjRemoteServerGateway(Mockito.mock(InspectionService.class), new ServerConnectionProperties());
        ServerConnectionHandle handle = new SshjServerConnectionHandle("127.0.0.1:22:gxl", sshClient);

        ServerShell serverShell = gateway.openShell(handle, "/home/gxl");

        Assertions.assertNotNull(serverShell);
        Mockito.verify(session, Mockito.never()).setEnvVar(Mockito.anyString(), Mockito.anyString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<PTYMode, Integer>> ptyModesCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(session).allocatePTY(Mockito.eq("xterm"), Mockito.eq(120), Mockito.eq(32), Mockito.eq(960), Mockito.eq(512), ptyModesCaptor.capture());
        Assertions.assertEquals(Integer.valueOf(0), ptyModesCaptor.getValue().get(PTYMode.ECHO));
    }

    @Test
    void shouldEnableSshKeepAliveWhenOpeningConnection() throws Exception {
        SSHClient sshClient = Mockito.mock(SSHClient.class);
        Connection connection = Mockito.mock(Connection.class);
        KeepAlive keepAlive = Mockito.mock(KeepAlive.class);
        Mockito.when(sshClient.getConnection()).thenReturn(connection);
        Mockito.when(connection.getKeepAlive()).thenReturn(keepAlive);

        InspectionService inspectionService = Mockito.mock(InspectionService.class);
        ServerConfig serverConfig = new ServerConfig("127.0.0.1", 22, "gxl", "secret", Collections.emptyList());
        Mockito.when(inspectionService.connectToServer(serverConfig)).thenReturn(sshClient);

        ServerConnectionProperties properties = new ServerConnectionProperties();
        properties.setSshKeepaliveIntervalSeconds(30);
        SshjRemoteServerGateway gateway = new SshjRemoteServerGateway(inspectionService, properties);

        ServerConnectionHandle handle = gateway.openConnection(serverConfig);

        Assertions.assertEquals("127.0.0.1:22:gxl", handle.getServerKey());
        Mockito.verify(keepAlive).setKeepAliveInterval(30);
    }
}
