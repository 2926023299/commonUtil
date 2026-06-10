package com.tool.otsutil.serverconnection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tool.otsutil.serverconnection.mapper.ServerConnectionBookmarkMapper;
import com.tool.otsutil.serverconnection.model.entity.ServerConnectionBookmark;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 服务器连接快捷路径Service实现
 */
@Service
public class ServerConnectionBookmarkServiceImpl extends ServiceImpl<ServerConnectionBookmarkMapper, ServerConnectionBookmark>
        implements ServerConnectionBookmarkService {

    @Override
    public List<ServerConnectionBookmark> listAll() {
        return list(new LambdaQueryWrapper<ServerConnectionBookmark>()
                .orderByAsc(ServerConnectionBookmark::getSortOrder)
                .orderByAsc(ServerConnectionBookmark::getId));
    }

    @Override
    public ServerConnectionBookmark saveBookmark(ServerConnectionBookmark bookmark) {
        Date now = new Date();
        if (bookmark.getId() == null) {
            ServerConnectionBookmark lastOne = lambdaQuery()
                    .select(ServerConnectionBookmark::getSortOrder)
                    .orderByDesc(ServerConnectionBookmark::getSortOrder)
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
