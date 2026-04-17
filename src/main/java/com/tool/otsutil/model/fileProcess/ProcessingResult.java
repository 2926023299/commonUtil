package com.tool.otsutil.model.fileProcess;

import lombok.Builder;
import lombok.Data;

/**
 * 处理结果
 */
@Data
@Builder
public class ProcessingResult {
    private ProcessingOutcome outcome;
    private String message;
    private Object data;
    private long processingTime;
    private Exception error;

    public boolean isSuccess() {
        return outcome == ProcessingOutcome.SUCCESS;
    }

    public boolean isFailure() {
        return outcome == ProcessingOutcome.FAILED;
    }

    public boolean isIgnored() {
        return outcome == ProcessingOutcome.IGNORED;
    }

    public static ProcessingResult success(String message) {
        return ProcessingResult.builder()
                .outcome(ProcessingOutcome.SUCCESS)
                .message(message)
                .processingTime(System.currentTimeMillis())
                .build();
    }

    public static ProcessingResult success(String message, Object data) {
        return ProcessingResult.builder()
                .outcome(ProcessingOutcome.SUCCESS)
                .message(message)
                .data(data)
                .processingTime(System.currentTimeMillis())
                .build();
    }

    public static ProcessingResult ignored(String message) {
        return ProcessingResult.builder()
                .outcome(ProcessingOutcome.IGNORED)
                .message(message)
                .processingTime(System.currentTimeMillis())
                .build();
    }

    public static ProcessingResult ignored(String message, Object data) {
        return ProcessingResult.builder()
                .outcome(ProcessingOutcome.IGNORED)
                .message(message)
                .data(data)
                .processingTime(System.currentTimeMillis())
                .build();
    }

    public static ProcessingResult failure(String message) {
        return ProcessingResult.builder()
                .outcome(ProcessingOutcome.FAILED)
                .message(message)
                .processingTime(System.currentTimeMillis())
                .build();
    }

    public static ProcessingResult failure(String message, Exception error) {
        return ProcessingResult.builder()
                .outcome(ProcessingOutcome.FAILED)
                .message(message)
                .error(error)
                .processingTime(System.currentTimeMillis())
                .build();
    }
}
