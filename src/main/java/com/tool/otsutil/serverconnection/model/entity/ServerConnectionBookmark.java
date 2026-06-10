package com.tool.otsutil.serverconnection.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 服务器连接快捷路径（收藏）实体
 */
@Data
@TableName("server_connection_bookmark")
public class ServerConnectionBookmark {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务器标识（ip:port:username）
     */
    @TableField("server_key")
    private String serverKey;

    /**
     * 自定义备注名称（可选，为空时显示原始IP）
     */
    @TableField("alias")
    private String alias;

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
