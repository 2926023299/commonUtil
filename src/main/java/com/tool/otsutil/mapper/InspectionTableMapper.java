package com.tool.otsutil.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tool.otsutil.model.entity.InspectionTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InspectionTableMapper extends BaseMapper<InspectionTable> {
	// 在这里添加自定义SQL方法
	List<InspectionTable> selectLatestByIpWithCondition(@Param("ip") String IP, @Param("updateTime") String updateTime, @Param("status") Integer status);

}