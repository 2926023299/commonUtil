package com.tool.otsutil.model.fileProcess;

/**
 * 文件事件类型
 */
public enum FileEventType {
	FILE_CREATED,     // 文件创建
	FILE_MODIFIED,    // 文件修改
	FILE_DELETED,     // 文件删除
	UPLOAD_STARTED,   // 上传开始
	UPLOAD_COMPLETED, // 上传完成
	PROCESS_STARTED,  // 处理开始
	PROCESS_COMPLETED,// 处理完成
	PROCESS_FAILED    // 处理失败
}
