package com.tool.otsutil.serverconnection.service;

import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.serverconnection.model.view.ServerConnectionView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class ServerCatalogServiceTest {

    @Test
    void shouldDeduplicateServersAndMergeJarNames() {
        InspectionConfig inspectionConfig = new InspectionConfig();
        inspectionConfig.setServers(Arrays.asList(
                new ServerConfig("10.0.0.1", 22, "root", "secret", Arrays.asList("app.jar:start.sh", "common.jar:start.sh")),
                new ServerConfig("10.0.0.1", 22, "root", "secret", Arrays.asList("common.jar:start.sh", "report.jar:start.sh")),
                new ServerConfig("10.0.0.2", 2222, "ops", "secret", Arrays.asList("worker.jar:start.sh"))
        ));

        ServerCatalogService service = new ServerCatalogService(inspectionConfig);
        List<ServerConnectionView> servers = service.listServers();

        Assertions.assertEquals(2, servers.size());
        Assertions.assertEquals("10.0.0.1:22:root", servers.get(0).getServerKey());
        Assertions.assertEquals(Arrays.asList("app.jar", "common.jar", "report.jar"), servers.get(0).getJars());
    }
}
