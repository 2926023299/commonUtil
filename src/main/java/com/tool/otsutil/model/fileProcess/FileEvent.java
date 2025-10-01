package com.tool.otsutil.model.fileProcess;

import lombok.Data;

import java.util.Map; /**
 * 文件事件
 */
@Data
public class FileEvent {
	private String eventId;
	private FileEventType eventType;
	private String filePath;
	private long fileSize;
	private long timestamp;
	private String monitorId;
	private Map<String, Object> metadata;
}
