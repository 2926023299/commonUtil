package com.tool.otsutil.serverconnection.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 远程文件快捷路径（书签）实体
 */
@Data
@TableName("remote_file_bookmark")
public class RemoteFileBookmark {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 书签名称
     */
    @TableField("label")
    private String label;

    /**
     * 远程路径
     */
    @TableField("path")
    private String path;

    /**
     * 服务器标识（ip:port:username），NULL 表示全局共享
     */
    @TableField("server_key")
    private String serverKey;

    /**
     * 排序序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private Date updateTime;
}
