package com.tool.otsutil.util;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.FileEventType;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.FileProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalFileMonitorServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldArchiveSuccessfulProcessingToBackupAndPublishCompletedEvent() throws Exception {
        RecordingPublisher publisher = new RecordingPublisher();
        FileProcessingStatusService statusService = new FileProcessingStatusService();
        UniversalFileMonitorService service = new UniversalFileMonitorService(publisher, statusService);
        FileMonitorConfig config = testConfig("success-monitor");
        registerMonitorContext(service, config);
        service.registerProcessor(new FixedResultProcessor("success", ProcessingResult.success("ok")));

        Path file = createFile("demo.txt");
        invokeTrigger(service, file, config);

        assertTrue(waitForPath(tempDir.resolve("backup").resolve("demo.txt")));
        assertEventPublished(publisher.events, FileEventType.PROCESS_COMPLETED);
        assertEventPublished(publisher.events, FileEventType.PROCESS_STARTED);
        assertEventNotPublished(publisher.events, FileEventType.PROCESS_FAILED);

        Map<String, Object> monitorStatus = service.getMonitorStatus(config.getId());
        assertNotNull(monitorStatus);
        List<?> recentResults = (List<?>) monitorStatus.get("recentResults");
        assertFalse(recentResults.isEmpty());
    }

    @Test
    void shouldArchiveFailedProcessingToErrorAndNotPublishCompletedEvent() throws Exception {
        RecordingPublisher publisher = new RecordingPublisher();
        FileProcessingStatusService statusService = new FileProcessingStatusService();
        UniversalFileMonitorService service = new UniversalFileMonitorService(publisher, statusService);
        FileMonitorConfig config = testConfig("failure-monitor");
        registerMonitorContext(service, config);
        service.registerProcessor(new FixedResultProcessor("failure", ProcessingResult.failure("boom")));

        Path file = createFile("failure.txt");
        invokeTrigger(service, file, config);

        assertTrue(waitForPath(tempDir.resolve("error").resolve("failure.txt")));
        assertEventPublished(publisher.events, FileEventType.PROCESS_FAILED);
        assertEventNotPublished(publisher.events, FileEventType.PROCESS_COMPLETED);
    }

    @Test
    void shouldArchiveUnsupportedFileToIgnoredWhenNoProcessorMatches() throws Exception {
        RecordingPublisher publisher = new RecordingPublisher();
        FileProcessingStatusService statusService = new FileProcessingStatusService();
        UniversalFileMonitorService service = new UniversalFileMonitorService(publisher, statusService);
        FileMonitorConfig config = testConfig("ignored-monitor");
        registerMonitorContext(service, config);

        Path file = createFile("ignored.txt");
        invokeTrigger(service, file, config);

        assertTrue(waitForPath(tempDir.resolve("ignored").resolve("ignored.txt")));
        assertEventPublished(publisher.events, FileEventType.PROCESS_IGNORED);
    }

    @Test
    void shouldFailWhenMultipleProcessorsMatchSameFile() throws Exception {
        RecordingPublisher publisher = new RecordingPublisher();
        FileProcessingStatusService statusService = new FileProcessingStatusService();
        UniversalFileMonitorService service = new UniversalFileMonitorService(publisher, statusService);
        FileMonitorConfig config = testConfig("conflict-monitor");
        registerMonitorContext(service, config);
        service.registerProcessor(new FixedResultProcessor("one", ProcessingResult.success("ok")));
        service.registerProcessor(new FixedResultProcessor("two", ProcessingResult.success("ok")));

        Path file = createFile("conflict.txt");
        invokeTrigger(service, file, config);

        assertTrue(waitForPath(tempDir.resolve("error").resolve("conflict.txt")));
        assertEventPublished(publisher.events, FileEventType.PROCESS_FAILED);
        assertEventNotPublished(publisher.events, FileEventType.PROCESS_STARTED);
    }

    @Test
    void shouldRespectMaxConcurrentLimit() throws Exception {
        RecordingPublisher publisher = new RecordingPublisher();
        FileProcessingStatusService statusService = new FileProcessingStatusService();
        UniversalFileMonitorService service = new UniversalFileMonitorService(publisher, statusService);
        FileMonitorConfig config = testConfig("concurrent-monitor");
        registerMonitorContext(service, config);

        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        service.registerProcessor(new BlockingProcessor(startedLatch, releaseLatch));

        Path firstFile = createFile("first.txt");
        Path secondFile = createFile("second.txt");

        invokeTrigger(service, firstFile, config);
        assertTrue(startedLatch.await(2, TimeUnit.SECONDS));

        invokeTrigger(service, secondFile, config);
        Thread.sleep(300);
        assertTrue(Files.exists(secondFile));
        assertFalse(Files.exists(tempDir.resolve("backup").resolve("second.txt")));

        releaseLatch.countDown();
        assertTrue(waitForPath(tempDir.resolve("backup").resolve("first.txt")));

        invokeTrigger(service, secondFile, config);
        assertTrue(waitForPath(tempDir.resolve("backup").resolve("second.txt")));
    }

    private FileMonitorConfig testConfig(String id) {
        return FileMonitorConfig.builder()
                .id(id)
                .directory(tempDir.toString())
                .filePattern("*.txt")
                .checkInterval(1)
                .maxConcurrent(1)
                .stabilityThreshold(1000)
                .build();
    }

    private Path createFile(String fileName) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.write(file, java.util.Arrays.asList("demo"), StandardCharsets.UTF_8);
        return file;
    }

    private void invokeTrigger(UniversalFileMonitorService service, Path file, FileMonitorConfig config) throws Exception {
        Method triggerMethod = UniversalFileMonitorService.class.getDeclaredMethod(
                "triggerFileProcessing", Path.class, FileMonitorConfig.class, FileEventType.class);
        triggerMethod.setAccessible(true);
        triggerMethod.invoke(service, file, config, FileEventType.UPLOAD_COMPLETED);
    }

    @SuppressWarnings("unchecked")
    private void registerMonitorContext(UniversalFileMonitorService service, FileMonitorConfig config) throws Exception {
        Field monitorConfigsField = UniversalFileMonitorService.class.getDeclaredField("monitorConfigs");
        monitorConfigsField.setAccessible(true);
        ((Map<String, FileMonitorConfig>) monitorConfigsField.get(service)).put(config.getId(), config);

        Field semaphoresField = UniversalFileMonitorService.class.getDeclaredField("monitorSemaphores");
        semaphoresField.setAccessible(true);
        ((Map<String, Semaphore>) semaphoresField.get(service)).put(config.getId(), new Semaphore(config.getMaxConcurrent()));
    }

    private boolean waitForPath(Path path) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(path)) {
                return true;
            }
            Thread.sleep(50);
        }
        return Files.exists(path);
    }

    private void assertEventPublished(List<FileEventType> events, FileEventType expected) {
        assertTrue(events.contains(expected), "expected event " + expected + " but was " + events);
    }

    private void assertEventNotPublished(List<FileEventType> events, FileEventType unexpected) {
        assertFalse(events.contains(unexpected), "unexpected event " + unexpected + " but was " + events);
    }

    private static class RecordingPublisher implements ApplicationEventPublisher {
        private final List<FileEventType> events = new CopyOnWriteArrayList<FileEventType>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof FileEvent) {
                events.add(((FileEvent) event).getEventType());
            }
        }
    }

    private static class FixedResultProcessor implements FileProcessor {
        private final String name;
        private final ProcessingResult result;

        private FixedResultProcessor(String name, ProcessingResult result) {
            this.name = name;
            this.result = result;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean supports(String filePath, boolean isStable) {
            return filePath.endsWith(".txt");
        }

        @Override
        public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
            return result;
        }
    }

    private static class BlockingProcessor implements FileProcessor {
        private final CountDownLatch startedLatch;
        private final CountDownLatch releaseLatch;

        private BlockingProcessor(CountDownLatch startedLatch, CountDownLatch releaseLatch) {
            this.startedLatch = startedLatch;
            this.releaseLatch = releaseLatch;
        }

        @Override
        public String getName() {
            return "blocking";
        }

        @Override
        public boolean supports(String filePath, boolean isStable) {
            return filePath.endsWith(".txt");
        }

        @Override
        public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
            startedLatch.countDown();
            try {
                releaseLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ProcessingResult.failure("interrupted", e);
            }
            return ProcessingResult.success("ok");
        }
    }
}
