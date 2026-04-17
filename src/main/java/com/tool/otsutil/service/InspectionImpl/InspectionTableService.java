package com.tool.otsutil.service.InspectionImpl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tool.otsutil.model.dto.inspection.InspectionPage;
import com.tool.otsutil.model.entity.InspectionTable;

import java.time.LocalDateTime;
import java.util.List;

public interface InspectionTableService extends IService<InspectionTable> {

	/**
	 * 分页查询检查表
	 * 根据IP、更新时间和状态进行过滤，获取最新的记录
	 * @param inspectionPage 分页参数
	 * @return 分页结果
	 */
	Page<InspectionTable> getPaginatedInspectionTable(InspectionPage inspectionPage);

	/**
	 * 根据IP和更新时间获取单条的检查记录
	 * @param ip IP地址
	 * @param update 更新时间
	 * @return 检查记录
	 */
	InspectionTable getInspectionByIp(String ip, String update);

	/**
	 * 根据IP获取该ip的历史查询记录
	 *
	 * @param inspectionPage 检查页面信息
	 * @return 检查页面信息
	 */
	Page<InspectionTable> getInspectionPageByIp(InspectionPage inspectionPage);

	List<InspectionTable> listLatestInspectionTable(String ip, String updateTime, Integer status);

	InspectionTable getInspectionByIp(String ip, LocalDateTime updateTime);

	List<InspectionTable> getRecentInspectionByIp(String ip, int limit);
}
