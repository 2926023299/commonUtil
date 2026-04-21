package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.InspectionImpl.InspectionService;
import net.schmizz.sshj.SSHClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class JarDeployProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void processShouldReturnIgnoredWhenNoDeploymentTargetMatches() throws IOException {
        JarDeployProcessor processor = new JarDeployProcessor();
        InspectionConfig inspectionConfig = new InspectionConfig();
        inspectionConfig.setServers(Arrays.asList(
                new ServerConfig("127.0.0.1", 22, "user", "pwd", Arrays.asList("other.jar:start.sh"))
        ));

        ReflectionTestUtils.setField(processor, "jarDeployConfig", inspectionConfig);
        ReflectionTestUtils.setField(processor, "inspectionService", Mockito.mock(InspectionService.class));
        ReflectionTestUtils.setField(processor, "targetPath", "/tmp/");
        ReflectionTestUtils.setField(processor, "nginxPath", "/nginx/");

        Path file = tempDir.resolve("demo.jar");
        Files.write(file, Arrays.asList("demo"), StandardCharsets.UTF_8);

        ProcessingResult result = processor.process(file.toString(), null, FileMonitorConfig.builder()
                .directory(tempDir.toString())
                .build());

        assertTrue(result.isIgnored());
    }

    @Test
    void processShouldReturnFailureWhenAnyDeploymentTargetFails() throws IOException {
        JarDeployProcessor processor = new JarDeployProcessor();
        InspectionConfig inspectionConfig = new InspectionConfig();
        inspectionConfig.setServers(Arrays.asList(
                new ServerConfig("127.0.0.1", 22, "user", "pwd", Arrays.asList("demo.jar:start.sh"))
        ));

        InspectionService inspectionService = Mockito.mock(InspectionService.class);
        when(inspectionService.connectToServer(any(ServerConfig.class))).thenThrow(new IOException("ssh down"));

        ReflectionTestUtils.setField(processor, "jarDeployConfig", inspectionConfig);
        ReflectionTestUtils.setField(processor, "inspectionService", inspectionService);
        ReflectionTestUtils.setField(processor, "targetPath", "/tmp/");
        ReflectionTestUtils.setField(processor, "nginxPath", "/nginx/");

        Path file = tempDir.resolve("demo.jar");
        Files.write(file, Arrays.asList("demo"), StandardCharsets.UTF_8);

        ProcessingResult result = processor.process(file.toString(), null, FileMonitorConfig.builder()
                .directory(tempDir.toString())
                .build());

        assertTrue(result.isFailure());
    }
}
