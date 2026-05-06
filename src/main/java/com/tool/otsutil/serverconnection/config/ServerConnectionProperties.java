package com.tool.otsutil.serverconnection.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "server-connections")
public class ServerConnectionProperties {

    /**
     * Whether to replace real SSH operations with a filesystem-backed mock gateway.
     */
    private boolean mockEnabled = false;

    /**
     * Idle minutes before an unused terminal session is closed. A value of 0 or less disables idle cleanup.
     */
    private int idleTimeoutMinutes = 0;

    /**
     * Cleanup interval for stale sessions.
     */
    private long cleanupDelayMs = 60_000L;

    /**
     * SSH transport keepalive interval in seconds. A value of 0 or less disables SSH keepalive.
     */
    private int sshKeepaliveIntervalSeconds = 30;
}
