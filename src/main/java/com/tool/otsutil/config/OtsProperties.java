package com.tool.otsutil.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("ots")
public class OtsProperties {
    private String endpoint;

    private String instance_name;

    private String access_key_id;

    private String access_key_secret;

    private String table_name;

    private String key;

    private String curveTemplatePath = "曲线.json";
}
