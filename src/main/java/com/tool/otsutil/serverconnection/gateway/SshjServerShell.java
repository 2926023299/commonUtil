package com.tool.otsutil.serverconnection.gateway;

import net.schmizz.sshj.connection.channel.direct.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class SshjServerShell implements ServerShell {

    private final Session session;
    private final Session.Shell shell;
    private final String initialPath;
    private final Charset charset;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final StringBuilder outputBuffer = new StringBuilder();
    private volatile boolean alive = false;

    public SshjServerShell(Session session, Session.Shell shell, String initialPath) {
        this(session, shell, initialPath, "UTF-8");
    }

    public SshjServerShell(Session session, Session.Shell shell, String initialPath, String charsetName) {
        this.session = session;
        this.shell = shell;
        this.initialPath = initialPath;
        this.charset = parseCharset(charsetName);
    }

    @Override
    public void start(ServerShellListener listener) throws IOException {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        listener.onStatus("connected", "shell connected");
        Thread readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop(listener);
            }
        }, "ssh-shell-" + session.getID());
        readerThread.setDaemon(true);
        readerThread.start();

        initializeShellPromptTracking();
    }

    @Override
    public void write(String data) throws IOException {
        if (data == null || data.isEmpty()) {
            return;
        }

        writeInternal(data);
    }

    @Override
    public void resize(int cols, int rows) throws IOException {
        int width = cols <= 0 ? 120 : cols;
        int height = rows <= 0 ? 32 : rows;
        shell.changeWindowDimensions(width, height, width * 8, height * 16);
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public void close() throws IOException {
        alive = false;
        try {
            shell.close();
        } finally {
            session.close();
        }
    }

    private Charset parseCharset(String charsetName) {
        if (charsetName == null || charsetName.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charsetName.trim());
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private void readLoop(ServerShellListener listener) {
        try {
            alive = true;
            InputStream inputStream = shell.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, read, charset);
                dispatchChunk(listener, chunk);
            }
            alive = false;
            flushBufferedOutput(listener, true);
            listener.onStatus("closed", "shell closed");
        } catch (Exception exception) {
            alive = false;
            listener.onStatus("error", exception.getMessage());
        }
    }

    private void dispatchChunk(ServerShellListener listener, String chunk) {
        synchronized (outputBuffer) {
            outputBuffer.append(chunk);
            flushBufferedOutput(listener, false);
        }
    }

    private void flushBufferedOutput(ServerShellListener listener, boolean flushAll) {
        synchronized (outputBuffer) {
            while (true) {
                int prefixIndex = outputBuffer.indexOf(TerminalPromptTracking.OSC_CWD_PREFIX);
                if (prefixIndex < 0) {
                    if (flushAll) {
                        if (outputBuffer.length() > 0) {
                            listener.onOutput(outputBuffer.toString());
                            outputBuffer.setLength(0);
                        }
                    } else {
                        int overlapLength = trailingPrefixOverlap(outputBuffer);
                        int safeLength = outputBuffer.length() - overlapLength;
                        if (safeLength > 0) {
                            listener.onOutput(outputBuffer.substring(0, safeLength));
                            outputBuffer.delete(0, safeLength);
                        }
                    }
                    return;
                }

                int suffixIndex = indexOfOscTerminator(outputBuffer, prefixIndex + TerminalPromptTracking.OSC_CWD_PREFIX.length());
                if (suffixIndex < 0) {
                    if (prefixIndex > 0) {
                        listener.onOutput(outputBuffer.substring(0, prefixIndex));
                        outputBuffer.delete(0, prefixIndex);
                    }
                    return;
                }

                if (prefixIndex > 0) {
                    listener.onOutput(outputBuffer.substring(0, prefixIndex));
                }

                String currentDirectory = outputBuffer.substring(prefixIndex + TerminalPromptTracking.OSC_CWD_PREFIX.length(), suffixIndex).trim();
                if (!currentDirectory.isEmpty()) {
                    listener.onCurrentDirectory(currentDirectory);
                }

                outputBuffer.delete(0, suffixIndex + 1);
            }
        }
    }

    private int trailingPrefixOverlap(CharSequence text) {
        int maxLength = Math.min(text.length(), TerminalPromptTracking.OSC_CWD_PREFIX.length() - 1);
        for (int length = maxLength; length > 0; length--) {
            boolean matches = true;
            for (int index = 0; index < length; index++) {
                if (text.charAt(text.length() - length + index) != TerminalPromptTracking.OSC_CWD_PREFIX.charAt(index)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return length;
            }
        }
        return 0;
    }

    private int indexOfOscTerminator(CharSequence text, int start) {
        for (int index = start; index < text.length(); index++) {
            if (text.charAt(index) == TerminalPromptTracking.OSC_TERMINATOR) {
                return index;
            }
        }
        return -1;
    }

    private void writeInternal(String data) throws IOException {
        OutputStream outputStream = shell.getOutputStream();
        outputStream.write(data.getBytes(charset));
        outputStream.flush();
    }

    private void initializeShellPromptTracking() throws IOException {
        writeInternal(buildBootstrapCommand());
    }

    private String buildBootstrapCommand() {
        StringBuilder bootstrap = new StringBuilder();
        bootstrap.append("PROMPT_COMMAND=")
                .append(shellQuote(TerminalPromptTracking.promptCommand()))
                .append("; export PROMPT_COMMAND; ");
        if (initialPath != null && !initialPath.trim().isEmpty()) {
            bootstrap.append("(cd ")
                    .append(shellQuote(initialPath))
                    .append(" >/dev/null 2>&1 || true); ");
        }
        bootstrap.append("clear; stty echo\n");
        return bootstrap.toString();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
