package com.tool.otsutil.serverconnection.model.request;

import lombok.Data;

/**
 * 保存快捷路径请求
 */
@Data
public class SaveBookmarkRequest {

    /**
     * 主键ID，为空时新增，非空时更新
     */
    private Long id;

    /**
     * 书签名称
     */
    private String label;

    /**
     * 远程路径
     */
    private String path;

    /**
     * 服务器标识（ip:port:username），为空表示全局共享
     */
    private String serverKey;

    /**
     * 排序序号（可选）
     */
    private Integer sortOrder;
}
