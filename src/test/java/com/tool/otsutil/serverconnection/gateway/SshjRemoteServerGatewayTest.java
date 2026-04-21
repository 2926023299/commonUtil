package com.tool.otsutil.serverconnection.gateway;

import com.tool.otsutil.service.InspectionImpl.InspectionService;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

class SshjRemoteServerGatewayTest {

    @Test
    void shouldOpenShellWithEchoDisabledBeforeBootstrap() throws Exception {
        SSHClient sshClient = Mockito.mock(SSHClient.class);
        Session session = Mockito.mock(Session.class);
        Session.Shell shell = Mockito.mock(Session.Shell.class);
        Mockito.when(sshClient.startSession()).thenReturn(session);
        Mockito.when(session.startShell()).thenReturn(shell);

        SshjRemoteServerGateway gateway = new SshjRemoteServerGateway(Mockito.mock(InspectionService.class));
        ServerConnectionHandle handle = new SshjServerConnectionHandle("127.0.0.1:22:gxl", sshClient);

        ServerShell serverShell = gateway.openShell(handle, "/home/gxl");

        Assertions.assertNotNull(serverShell);
        Mockito.verify(session, Mockito.never()).setEnvVar(Mockito.anyString(), Mockito.anyString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<PTYMode, Integer>> ptyModesCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(session).allocatePTY(Mockito.eq("xterm"), Mockito.eq(120), Mockito.eq(32), Mockito.eq(960), Mockito.eq(512), ptyModesCaptor.capture());
        Assertions.assertEquals(Integer.valueOf(0), ptyModesCaptor.getValue().get(PTYMode.ECHO));
    }
}
