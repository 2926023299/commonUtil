package com.tool.otsutil.serverconnection.gateway;

final class TerminalPromptTracking {

    static final String OSC_CWD_PREFIX = "\u001b]633;Cwd=";
    static final char OSC_TERMINATOR = '\u0007';

    private TerminalPromptTracking() {
    }

    static String promptCommand() {
        return "printf '\\033]633;Cwd=%s\\007' \"$PWD\"";
    }
}
