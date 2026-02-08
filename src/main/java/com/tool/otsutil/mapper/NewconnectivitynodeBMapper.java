package com.tool.otsutil.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tool.otsutil.model.entity.NewConnectivitynodeB;

import java.util.List;

@Mapper
public interface NewconnectivitynodeBMapper extends BaseMapper<NewConnectivitynodeB> {
	int batchReplaceInsert(List<NewConnectivitynodeB> list);
}
