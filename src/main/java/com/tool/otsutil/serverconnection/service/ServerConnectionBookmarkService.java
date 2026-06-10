package com.tool.otsutil.serverconnection.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tool.otsutil.serverconnection.model.entity.ServerConnectionBookmark;

import java.util.List;

/**
 * 服务器连接快捷路径Service
 */
public interface ServerConnectionBookmarkService extends IService<ServerConnectionBookmark> {

    /**
     * 查询所有服务器快捷路径，按 sort_order 升序
     */
    List<ServerConnectionBookmark> listAll();

    /**
     * 新增或更新。id 为空时新增，非空时更新。
     */
    ServerConnectionBookmark saveBookmark(ServerConnectionBookmark bookmark);

    /**
     * 根据ID删除
     */
    void removeBookmark(Long id);
}
