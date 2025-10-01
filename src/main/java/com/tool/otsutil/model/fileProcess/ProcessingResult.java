package com.tool.otsutil.model.fileProcess;

import lombok.Builder;
import lombok.Data; /**
 * 处理结果
 */
@Data
@Builder
public class ProcessingResult {
	private boolean success;
	private String message;
	private Object data;
	private long processingTime;
	private Exception error;

	public static ProcessingResult success(String message) {
		return ProcessingResult.builder()
				.success(true)
				.message(message)
				.processingTime(System.currentTimeMillis())
				.build();
	}

	public static ProcessingResult success(String message, Object data) {
		return ProcessingResult.builder()
				.success(true)
				.message(message)
				.data(data)
				.processingTime(System.currentTimeMillis())
				.build();
	}

	public static ProcessingResult failure(String message) {
		return ProcessingResult.builder()
				.success(false)
				.message(message)
				.processingTime(System.currentTimeMillis())
				.build();
	}

	public static ProcessingResult failure(String message, Exception error) {
		return ProcessingResult.builder()
				.success(false)
				.message(message)
				.error(error)
				.processingTime(System.currentTimeMillis())
				.build();
	}
}
