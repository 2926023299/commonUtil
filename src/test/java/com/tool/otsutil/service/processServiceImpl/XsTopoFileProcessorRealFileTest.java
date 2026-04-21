package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.mapper.TopoDeviceMapper;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"app.file.monitor.enabled=false"})
@EnabledIfSystemProperty(named = "xstopo.real.log.path", matches = ".+")
class XsTopoFileProcessorRealFileTest {

    @Autowired
    private TopoDeviceMapper topoDeviceMapper;

    @Test
    void processRealFileShouldGenerateExcel() throws Exception {
        String filePath = System.getProperty("xstopo.real.log.path");
        Path outputDir = Paths.get("target", "xstopo-real-output");
        if (Files.exists(outputDir)) {
            Files.walk(outputDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
        Files.createDirectories(outputDir);

        XsTopoFileProcessorImpl processor = new XsTopoFileProcessorImpl(topoDeviceMapper, outputDir.toAbsolutePath().toString());
        FileMonitorConfig config = FileMonitorConfig.builder()
                .directory(Paths.get(filePath).getParent().toString())
                .build();
        ProcessingResult result = processor.process(filePath, null, config);

        assertTrue(result.isSuccess());
        assertTrue(((Number) result.getData()).intValue() > 0);
        assertTrue(Files.list(outputDir).anyMatch(path -> path.getFileName().toString().endsWith(".xlsx")));
        System.out.println("XS_TOPO_REAL_OUTPUT_DIR=" + outputDir.toAbsolutePath());
    }
}
