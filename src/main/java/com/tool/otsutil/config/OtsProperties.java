package com.tool.otsutil.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("ots")
public class OtsProperties {
    @Value("endpoint")
    String endpoint;

    @Value("instance_name")
    String instance_name;

    @Value("access_key_id")
    String access_key_id;

    @Value("access_key_secret")
    String access_key_secret;

    @Value("table_name")
    String table_name;

    @Value("key")
    String key;
}
