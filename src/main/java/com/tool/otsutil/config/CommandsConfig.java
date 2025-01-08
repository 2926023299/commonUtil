package com.tool.otsutil.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "commands")
public class CommandsConfig {
    private final Map<String, String> command = new HashMap<>();
}
