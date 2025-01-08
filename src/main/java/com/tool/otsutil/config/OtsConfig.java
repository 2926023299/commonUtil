package com.tool.otsutil.config;

import com.alicloud.openservices.tablestore.SyncClient;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtsConfig {

    @Autowired
    private OtsProperties otsProperties;

    @Bean
    public SyncClient otsClient() {
        String endpoint = otsProperties.getEndpoint();
        String accessKeyId = otsProperties.getAccess_key_id();
        String accessKeySecret = otsProperties.getAccess_key_secret();
        String instanceName = otsProperties.getInstance_name();
        return new SyncClient(endpoint, accessKeyId, accessKeySecret, instanceName);
    }
}
