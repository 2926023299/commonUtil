package com.tool.otsutil.serverconnection.model.request;

import lombok.Data;

/**
 * 保存服务器连接快捷路径请求
 */
@Data
public class SaveServerBookmarkRequest {

    /**
     * 主键ID，为空时新增，非空时更新
     */
    private Long id;

    /**
     * 服务器标识（ip:port:username）
     */
    private String serverKey;

    /**
     * 自定义备注名称（可选）
     */
    private String alias;

    /**
     * 排序序号（可选）
     */
    private Integer sortOrder;
}
