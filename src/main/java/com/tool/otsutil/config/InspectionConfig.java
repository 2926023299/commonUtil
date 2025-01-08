package com.tool.otsutil.config;

import com.tool.otsutil.model.dto.inspection.ServerConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "inspection")
public class InspectionConfig {

    private List<ServerConfig> servers;

}