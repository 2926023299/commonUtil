package com.tool.otsutil.serverconnection.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tool.otsutil.serverconnection.model.entity.RemoteFileBookmark;

import java.util.List;

/**
 * 远程文件快捷路径Service
 */
public interface RemoteFileBookmarkService extends IService<RemoteFileBookmark> {

    /**
     * 查询书签：返回全局共享 + 指定服务器的书签，按 sort_order 升序
     *
     * @param serverKey 服务器标识，为 null 时只返回全局共享书签
     */
    List<RemoteFileBookmark> listByServer(String serverKey);

    /**
     * 新增或更新书签。id 为空时新增，非空时更新。
     */
    RemoteFileBookmark saveBookmark(RemoteFileBookmark bookmark);

    /**
     * 根据ID删除书签
     */
    void removeBookmark(Long id);
}
