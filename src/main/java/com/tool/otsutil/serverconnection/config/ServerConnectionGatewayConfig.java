package com.tool.otsutil.serverconnection.config;

import com.tool.otsutil.serverconnection.gateway.MockRemoteServerGateway;
import com.tool.otsutil.serverconnection.gateway.RemoteServerGateway;
import com.tool.otsutil.serverconnection.gateway.SshjRemoteServerGateway;
import com.tool.otsutil.service.InspectionImpl.InspectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServerConnectionGatewayConfig {

    @Bean
    @ConditionalOnProperty(prefix = "server-connections", name = "mock-enabled", havingValue = "true")
    public RemoteServerGateway mockRemoteServerGateway() {
        return new MockRemoteServerGateway();
    }

    @Bean
    @ConditionalOnProperty(prefix = "server-connections", name = "mock-enabled", havingValue = "false", matchIfMissing = true)
    public RemoteServerGateway sshjRemoteServerGateway(InspectionService inspectionService,
                                                       ServerConnectionProperties properties) {
        return new SshjRemoteServerGateway(inspectionService, properties);
    }
}
