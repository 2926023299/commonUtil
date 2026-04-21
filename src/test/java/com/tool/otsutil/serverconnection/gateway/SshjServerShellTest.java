package com.tool.otsutil.serverconnection.gateway;

import net.schmizz.sshj.connection.channel.direct.Session;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class SshjServerShellTest {

    @Test
    void shouldPreservePromptTailWhenOscSequenceIsSplitAcrossChunks() throws Exception {
        PipedOutputStream remoteWriter = new PipedOutputStream();
        PipedInputStream inputStream = new PipedInputStream(remoteWriter);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Session session = Mockito.mock(Session.class);
        Session.Shell shell = Mockito.mock(Session.Shell.class);
        Mockito.when(session.getID()).thenReturn(7);
        Mockito.when(shell.getInputStream()).thenReturn(inputStream);
        Mockito.when(shell.getOutputStream()).thenReturn(outputStream);

        SshjServerShell serverShell = new SshjServerShell(session, shell, "/home/gxl");
        RecordingListener listener = new RecordingListener();
        serverShell.start(listener);

        remoteWriter.write("\u001b]633;Cw".getBytes(StandardCharsets.UTF_8));
        remoteWriter.flush();
        remoteWriter.write("d=/home/gxl\u0007gxl@DESKTOP-6794LNS:~$ ".getBytes(StandardCharsets.UTF_8));
        remoteWriter.close();

        Assertions.assertTrue(listener.awaitClosed(), "reader thread did not finish");
        Assertions.assertEquals("/home/gxl", listener.getLastCwd());
        Assertions.assertTrue(listener.getOutput().contains("gxl@DESKTOP-6794LNS:~$ "));
        Assertions.assertFalse(listener.getOutput().contains("gxl@DESKTO "));
    }

    @Test
    void shouldBootstrapShellWithoutInjectingProbeExportsIntoInteractiveStream() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Session session = Mockito.mock(Session.class);
        Session.Shell shell = Mockito.mock(Session.Shell.class);
        Mockito.when(session.getID()).thenReturn(8);
        Mockito.when(shell.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        Mockito.when(shell.getOutputStream()).thenReturn(outputStream);

        SshjServerShell serverShell = new SshjServerShell(session, shell, "/srv/workspace");
        RecordingListener listener = new RecordingListener();
        serverShell.start(listener);

        Assertions.assertTrue(listener.awaitClosed(), "reader thread did not finish");
        String bootstrap = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        Assertions.assertTrue(bootstrap.startsWith("PROMPT_COMMAND="));
        Assertions.assertTrue(bootstrap.contains("; export PROMPT_COMMAND; "));
        Assertions.assertTrue(bootstrap.endsWith("(cd '/srv/workspace' >/dev/null 2>&1 || true); clear; stty echo\n"));
        Assertions.assertEquals(1L, bootstrap.chars().filter(ch -> ch == '\n').count());
    }

    private static final class RecordingListener implements ServerShellListener {

        private final StringBuilder output = new StringBuilder();
        private final CountDownLatch closedLatch = new CountDownLatch(1);
        private volatile String lastCwd;

        @Override
        public synchronized void onOutput(String data) {
            output.append(data);
        }

        @Override
        public void onStatus(String status, String message) {
            if ("closed".equals(status) || "error".equals(status)) {
                closedLatch.countDown();
            }
        }

        @Override
        public void onCurrentDirectory(String cwd) {
            lastCwd = cwd;
        }

        public boolean awaitClosed() throws InterruptedException {
            return closedLatch.await(3, TimeUnit.SECONDS);
        }

        public synchronized String getOutput() {
            return output.toString();
        }

        public String getLastCwd() {
            return lastCwd;
        }
    }
}
