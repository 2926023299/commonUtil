package com.tool.otsutil.service.InspectionImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tool.otsutil.model.dto.inspection.InspectionPage;
import com.tool.otsutil.model.entity.InspectionTable;
import com.tool.otsutil.mapper.InspectionTableMapper;
import com.tool.otsutil.util.InspectionViewSupport;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class InspectionTableServiceImpl extends ServiceImpl<InspectionTableMapper, InspectionTable> implements InspectionTableService {

	/**
	 * 分页查询检查表
	 * 根据IP、更新时间和状态进行过滤，获取最新的记录
	 *
	 * @param inspectionPage 分页参数
	 * @return 分页结果
	 */
	@Override
	public Page<InspectionTable> getPaginatedInspectionTable(InspectionPage inspectionPage) {

		Page<InspectionTable> page = new Page<>(inspectionPage.getPage(), inspectionPage.getPageSize());

		log.info(String.valueOf(inspectionPage));

		// 使用 MyBatis 查询去重后的数据
		List<InspectionTable> filteredList = baseMapper.selectLatestByIpWithCondition(inspectionPage.getIP(), inspectionPage.getUpdateTime(), inspectionPage.getStatus());

		// 手动分页
		int total = filteredList.size();
		int start = (int) ((page.getCurrent() - 1) * page.getSize());
		int end = Math.min(start + (int) page.getSize(), total);
		List<InspectionTable> pagedList = start > total ? Collections.emptyList() : filteredList.subList(start, end);

		page.setRecords(pagedList);
		page.setTotal(total);

		return page;
	}

	@Override
	public InspectionTable getInspectionByIp(String ip, String update) {
		LocalDateTime updateTime = InspectionViewSupport.parseDateTime(update);
		return getInspectionByIp(ip, updateTime);
	}

	@Override
	public InspectionTable getInspectionByIp(String ip, LocalDateTime updateTime) {
		LambdaQueryWrapper<InspectionTable> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(InspectionTable::getIP, ip);
		if (updateTime != null) {
			queryWrapper.eq(InspectionTable::getUpdateTime, updateTime);
		}
		return this.getOne(queryWrapper);
	}

	@Override
	public Page<InspectionTable> getInspectionPageByIp(InspectionPage inspectionPage) {
		Page<InspectionTable> page = new Page<>(inspectionPage.getPage(), inspectionPage.getPageSize());

		LambdaQueryWrapper<InspectionTable> queryWrapper = new LambdaQueryWrapper<>();
		if (inspectionPage.getIP() != null && !inspectionPage.getIP().isEmpty()) {
			queryWrapper.eq(InspectionTable::getIP, inspectionPage.getIP());
		}
		// 根据更新时间进行降序排序
		queryWrapper.orderByDesc(InspectionTable::getUpdateTime);

		this.page(page, queryWrapper);

		return page;
	}

	@Override
	public List<InspectionTable> listLatestInspectionTable(String ip, String updateTime, Integer status) {
		return baseMapper.selectLatestByIpWithCondition(ip, updateTime, status);
	}

	@Override
	public List<InspectionTable> getRecentInspectionByIp(String ip, int limit) {
		int safeLimit = limit <= 0 ? 1 : limit;

		LambdaQueryWrapper<InspectionTable> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(InspectionTable::getIP, ip);
		queryWrapper.orderByDesc(InspectionTable::getUpdateTime);
		queryWrapper.last("LIMIT " + safeLimit);

		return this.list(queryWrapper);
	}
}
