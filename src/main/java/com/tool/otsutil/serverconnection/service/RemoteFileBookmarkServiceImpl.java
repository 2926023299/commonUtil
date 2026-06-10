package com.tool.otsutil.serverconnection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tool.otsutil.serverconnection.mapper.RemoteFileBookmarkMapper;
import com.tool.otsutil.serverconnection.model.entity.RemoteFileBookmark;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 远程文件快捷路径Service实现
 */
@Service
public class RemoteFileBookmarkServiceImpl extends ServiceImpl<RemoteFileBookmarkMapper, RemoteFileBookmark>
        implements RemoteFileBookmarkService {

    @Override
    public List<RemoteFileBookmark> listByServer(String serverKey) {
        LambdaQueryWrapper<RemoteFileBookmark> wrapper = new LambdaQueryWrapper<>();
        if (serverKey != null && !serverKey.isEmpty()) {
            // 全局共享（server_key 为 null）+ 指定服务器的书签
            wrapper.and(w -> w.isNull(RemoteFileBookmark::getServerKey)
                    .or()
                    .eq(RemoteFileBookmark::getServerKey, serverKey));
        } else {
            // 只返回全局共享的书签
            wrapper.isNull(RemoteFileBookmark::getServerKey);
        }
        wrapper.orderByAsc(RemoteFileBookmark::getSortOrder)
                .orderByAsc(RemoteFileBookmark::getId);
        return list(wrapper);
    }

    @Override
    public RemoteFileBookmark saveBookmark(RemoteFileBookmark bookmark) {
        Date now = new Date();
        if (bookmark.getId() == null) {
            // 新增：自动设置排序（追加到末尾）
            RemoteFileBookmark lastOne = lambdaQuery()
                    .select(RemoteFileBookmark::getSortOrder)
                    .orderByDesc(RemoteFileBookmark::getSortOrder)
                    .last("LIMIT 1")
                    .one();
            int nextOrder = (lastOne != null && lastOne.getSortOrder() != null) ? lastOne.getSortOrder() + 1 : 0;
            bookmark.setSortOrder(nextOrder);
            bookmark.setCreateTime(now);
            bookmark.setUpdateTime(now);
            save(bookmark);
        } else {
            bookmark.setUpdateTime(now);
            updateById(bookmark);
        }
        return bookmark;
    }

    @Override
    public void removeBookmark(Long id) {
        removeById(id);
    }
}
