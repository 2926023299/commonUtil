package com.tool.otsutil.service;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import com.tool.otsutil.mapper.NewconnectivitynodeBMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.tool.otsutil.mapper.ConnectivitynodeBMapper;
import com.tool.otsutil.model.entity.ConnectivityForTmp;
import com.tool.otsutil.model.entity.ConnectivitynodeB;
import com.tool.otsutil.model.entity.NewConnectivitynodeB;
import com.tool.otsutil.model.entity.NodeRdflistB;
import com.tool.otsutil.model.entity.RdflistB;
import com.tool.otsutil.service.tmp.ConnectivityForTmpService;
import com.tool.otsutil.service.tmp.ConnectivitynodeBService;
import com.tool.otsutil.service.tmp.NewconnectivitynodeBService;
import com.tool.otsutil.service.tmp.NodeRdflistBService;
import com.tool.otsutil.service.tmp.RdflistBService;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ConnectivityCreate {
	@Autowired
	private ConnectivityForTmpService connectivityForTmpService;

	@Autowired
	private NodeRdflistBService nodeRdflistBService;

	@Autowired
	private RdflistBService rdflistBService;

	@Autowired
	private ConnectivitynodeBService connectivitynodeBService;

	@Autowired
	private ConnectivitynodeBMapper connectivitynodeBMapper;

	@Autowired
	private NewconnectivitynodeBMapper newconnectivitynodeBMapper;

	@Autowired
	private NewconnectivitynodeBService newconnectivitynodeBService;

	/**
	 * 处理Excel文件，将数据写入MySQL
	 * Excel包含多个sheet，只有第一个sheet有表头
	 * 列对应：设备GISRDFID、设备EMSRDFID、连接点端号、多电源标示、连接点gisrdfid、连接点emsrdfid
	 * 对应connectivityForTmp类
	 *
	 * @param excelFilePath Excel文件路径
	 */
	public void processConnectivityExcel(String excelFilePath) {
		log.info("开始处理Excel文件: {}", excelFilePath);

		File excelFile = new File(excelFilePath);
		if (!excelFile.exists()) {
			log.error("Excel文件不存在: {}", excelFilePath);
			return;
		}

		// 创建监听器
		ExcelListener listener = new ExcelListener();

		// 读取Excel文件的所有sheet
		EasyExcel.read(excelFile, listener).doReadAll();

		// 保存数据到MySQL
		List<ConnectivityForTmp> dataList = listener.getDataList();
		log.info("Excel文件读取完成，共{}条数据", dataList.size());

		if (!dataList.isEmpty()) {
			// 批量保存数据
			int batchSize = 5000;
			int totalSaved = 0;

			for (int i = 0; i < dataList.size(); i += batchSize) {
				int endIndex = Math.min(i + batchSize, dataList.size());
				List<ConnectivityForTmp> batchList = dataList.subList(i, endIndex);

				boolean saved = connectivityForTmpService.saveBatch(batchList);
				if (saved) {
					totalSaved += batchList.size();
					log.info("已保存第{}批数据，共{}条", i / batchSize + 1, batchList.size());
				} else {
					log.error("保存第{}批数据失败", i / batchSize + 1);
				}
			}

			log.info("数据保存完成，共保存{}条数据", totalSaved);
		} else {
			log.info("Excel文件中无数据");

			//connectivityForTmpService.remove(null);
			//log.info("已清空connectivityForTmp表数据");
		}
	}

	public void processConnectivityExcel2toNewConnectivityB(String excelFilePath) {
		log.info("开始处理Excel文件: {}", excelFilePath);

		File excelFile = new File(excelFilePath);
		if (!excelFile.exists()) {
			log.error("Excel文件不存在: {}", excelFilePath);
			return;
		}

		// 创建监听器
		ExcelListener listener = new ExcelListener();

		// 读取Excel文件的所有sheet
		EasyExcel.read(excelFile, listener).doReadAll();

		// 保存数据到MySQL
		List<ConnectivityForTmp> dataList = listener.getDataList();
		log.info("Excel文件读取完成，共{}条数据", dataList.size());

		// 预加载所有需要的设备ID映射，避免在循环中多次查询数据库
		Set<String> allDeviceGISRDFIDs = new HashSet<>();
		Set<String> allConnectivityPointGISRDFIDs = new HashSet<>();
		
		for (ConnectivityForTmp tmp : dataList) {
			if (tmp.getDeviceGISRDFID() != null) {
				allDeviceGISRDFIDs.add(tmp.getDeviceGISRDFID());
			}
			if (tmp.getConnectivityPointGISRDFID() != null) {
				allConnectivityPointGISRDFIDs.add(tmp.getConnectivityPointGISRDFID());
			}
		}
		
		// 批量查询设备和连接点映射，只有在集合不为空时才执行查询
		Map<String, RdflistB> rdflistMap = new HashMap<>();
		if (!allDeviceGISRDFIDs.isEmpty()) {
			List<RdflistB> rdflistBList = rdflistBService.list(
				new LambdaQueryWrapper<RdflistB>().in(RdflistB::getGISRdfID, allDeviceGISRDFIDs)
			);
			rdflistMap = rdflistBList.stream()
				.collect(Collectors.toMap(RdflistB::getGISRdfID, Function.identity(), (existing, replacement) -> existing));
		}
		
		Map<String, NodeRdflistB> nodeRdflistMap = new HashMap<>();
		if (!allConnectivityPointGISRDFIDs.isEmpty()) {
			List<NodeRdflistB> nodeRdflistBList = nodeRdflistBService.list(
				new LambdaQueryWrapper<NodeRdflistB>().in(NodeRdflistB::getGISRdfID, allConnectivityPointGISRDFIDs)
			);
			nodeRdflistMap = nodeRdflistBList.stream()
				.collect(Collectors.toMap(NodeRdflistB::getGISRdfID, Function.identity(), (existing, replacement) -> existing));
		}

		int processedCount = 0;
		List<NewConnectivitynodeB> newConnectivitynodeBList = new ArrayList<>();
		// 使用Set来跟踪已经存在的双主键组合，防止重复
		Set<String> primaryKeySet = new HashSet<>();

		for (ConnectivityForTmp connectivityForTmp : dataList) {

			processedCount++;
			if (processedCount % 5000 == 0) {
				log.info("已处理{}条数据", processedCount);
			}

			String deviceGISRDFID = connectivityForTmp.getDeviceGISRDFID();
			String deviceEMSRDFID = connectivityForTmp.getDeviceEMSRDFID();
			String connectivityPointGISRDFID = connectivityForTmp.getConnectivityPointGISRDFID();
			String connectivityPointEMSRDFID = connectivityForTmp.getConnectivityPointEMSRDFID();

			String newDeviceGISRDFID = deviceGISRDFID;
			String newConnectivityPointGISRDFID = connectivityPointGISRDFID;
			String newDeviceEMSRDFID = deviceEMSRDFID;
			String newConnectivityPointEMSRDFID = connectivityPointEMSRDFID;

			// 识别字符类型并且剔除掉所有的非数字字符
			if (!StrUtil.isEmptyIfStr(deviceGISRDFID)) {
				newDeviceGISRDFID = deviceGISRDFID.replaceAll("[^0-9]", "");
				newDeviceGISRDFID += deviceGISRDFID.length();
			}
			if (!StrUtil.isEmptyIfStr(connectivityPointGISRDFID)) {
				newConnectivityPointGISRDFID = connectivityPointGISRDFID.replaceAll("[^0-9]", "");
				newConnectivityPointGISRDFID += connectivityPointGISRDFID.length();
			}
			if (!StrUtil.isEmptyIfStr(deviceEMSRDFID)) {
				newDeviceEMSRDFID = deviceEMSRDFID.replaceAll("[^0-9]", "");
				newDeviceEMSRDFID += deviceEMSRDFID.length();
			}
			if (!StrUtil.isEmptyIfStr(connectivityPointEMSRDFID)) {
				newConnectivityPointEMSRDFID = connectivityPointEMSRDFID.replaceAll("[^0-9]", "");
				newConnectivityPointEMSRDFID += connectivityPointEMSRDFID.length();
			}

			NewConnectivitynodeB newConnectivitynodeB = new NewConnectivitynodeB();

			// 从预加载的映射中获取设备ID
			RdflistB rdflistB = rdflistMap.get(deviceGISRDFID);
			if (ObjUtil.isNotNull(rdflistB) && !StrUtil.isEmptyIfStr(rdflistB.getDeviceID())) {
				newConnectivitynodeB.setEquipment_ID(rdflistB.getDeviceID());
			} else if (!StrUtil.isEmptyIfStr(newDeviceGISRDFID)) {
				try {
					BigDecimal equipmentID = new BigDecimal(newDeviceGISRDFID);
					newConnectivitynodeB.setEquipment_ID(equipmentID);
				} catch (NumberFormatException e) {
					log.warn("设备GISRDFID格式错误: {}", newDeviceGISRDFID);
				}
			} else if (!StrUtil.isEmptyIfStr(newDeviceEMSRDFID)) {
				try {
					BigDecimal equipmentID = new BigDecimal(newDeviceEMSRDFID);
					newConnectivitynodeB.setEquipment_ID(equipmentID);
				} catch (NumberFormatException e) {
					log.warn("设备EMSRDFID格式错误: {}", newDeviceEMSRDFID);
				}
			}

			// 赋值连接点端号
			newConnectivitynodeB.setSequence(connectivityForTmp.getConnectivityPointEndID());

			// 赋值多电源标示
			newConnectivitynodeB.setMultSrcFlag(connectivityForTmp.getMultiPowerFlag());

			// 从预加载的映射中获取连接点ID
			NodeRdflistB nodeRdflistB = nodeRdflistMap.get(connectivityPointGISRDFID);
			if (ObjUtil.isNotNull(nodeRdflistB) && !StrUtil.isEmptyIfStr(nodeRdflistB.getDeviceID())) {
				newConnectivitynodeB.setID(nodeRdflistB.getDeviceID());
			} else if (!StrUtil.isEmptyIfStr(newConnectivityPointGISRDFID)) {
				try {
					BigDecimal id = new BigDecimal(newConnectivityPointGISRDFID);
					newConnectivitynodeB.setID(id);
				} catch (NumberFormatException e) {
					log.warn("连接点GISRDFID格式错误: {}", newConnectivityPointGISRDFID);
				}
			} else if (!StrUtil.isEmptyIfStr(newConnectivityPointEMSRDFID)) {
				try {
					BigDecimal id = new BigDecimal(newConnectivityPointEMSRDFID);
					newConnectivitynodeB.setID(id);
				} catch (NumberFormatException e) {
					log.warn("连接点EMSRDFID格式错误: {}", newConnectivityPointEMSRDFID);
				}
			}

			if (newConnectivitynodeB.getEquipment_ID() == null || newConnectivitynodeB.getSequence() == null) {
				continue;
			}
			
			// 生成双主键的唯一标识
			String primaryKey = newConnectivitynodeB.getEquipment_ID().toString() + "_" + newConnectivitynodeB.getSequence().toString();
			
			// 检查双主键是否已经存在
			if (primaryKeySet.contains(primaryKey)) {
				continue; // 跳过重复的双主键组合
			}
			
			primaryKeySet.add(primaryKey);
			newConnectivitynodeBList.add(newConnectivitynodeB);

			// 当列表达到50000条时进行处理
			if (newConnectivitynodeBList.size() % 50000 == 0) {
				//保存结果
				newconnectivitynodeBMapper.batchReplaceInsert(newConnectivitynodeBList);

				log.info("已写入{}条数据到connectivityfortmp", newConnectivitynodeBList.size());

				// 清空列表和主键集合
				newConnectivitynodeBList.clear();
				primaryKeySet.clear();
			}
		}

		// 处理剩余数据
		if (!newConnectivitynodeBList.isEmpty()) {
			// 保存结果
			log.info("开始保存剩余数据，共{}条数据", newConnectivitynodeBList.size());
			if (!newConnectivitynodeBList.isEmpty()) {
				//newconnectivitynodeBService.saveOrUpdateBatch(newConnectivitynodeBList);
				newconnectivitynodeBMapper.batchReplaceInsert(newConnectivitynodeBList);
			}
		}
	}

	/**
	 * Excel监听器，用于读取Excel数据
	 */
	private class ExcelListener extends AnalysisEventListener<Map<Integer, String>> {
		private List<ConnectivityForTmp> dataList = new ArrayList<>();
		private boolean isFirstSheet = true;

		@Override
		public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
			// 获取当前sheet索引
			int sheetIndex = context.readSheetHolder().getSheetNo();

			// 对于第一个sheet，跳过表头行
			if (isFirstSheet && sheetIndex == 0 && context.readRowHolder().getRowIndex() == 0) {
				log.info("跳过第一个sheet的表头行");
				return;
			}

			// 创建ConnectivityForTmp对象
			ConnectivityForTmp entity = new ConnectivityForTmp();

			// 填充数据，根据列索引对应字段
			// 0: 设备GISRDFID
			// 1: 设备EMSRDFID
			// 2: 连接点端号
			// 3: 多电源标示
			// 4: 连接点gisrdfid
			// 5: 连接点emsrdfid

			// 设备GISRDFID
			String deviceGISRDFID = rowData.get(0);
			entity.setDeviceGISRDFID(deviceGISRDFID);

			// 设备EMSRDFID
			String deviceEMSRDFID = rowData.get(1);
			entity.setDeviceEMSRDFID(deviceEMSRDFID);

			// 连接点端号 (BigDecimal)
			String sequenceStr = rowData.get(2);
			if (sequenceStr != null && !sequenceStr.trim().isEmpty()) {
				try {
					BigDecimal sequence = new BigDecimal(sequenceStr);
					entity.setConnectivityPointEndID(sequence);
				} catch (NumberFormatException e) {
					log.warn("连接点端号格式错误: {}", sequenceStr);
				}
			}

			// 多电源标示 (BigDecimal)
			String multiPowerFlagStr = rowData.get(3);
			if (multiPowerFlagStr != null && !multiPowerFlagStr.trim().isEmpty()) {
				try {
					BigDecimal multiPowerFlag = new BigDecimal(multiPowerFlagStr);
					entity.setMultiPowerFlag(multiPowerFlag);
				} catch (NumberFormatException e) {
					log.warn("多电源标示格式错误: {}", multiPowerFlagStr);
				}
			}

			// 连接点gisrdfid
			String connectivityPointGISRDFID = rowData.get(4);
			entity.setConnectivityPointGISRDFID(connectivityPointGISRDFID);

			// 连接点emsrdfid
			String connectivityPointEMSRDFID = rowData.get(5);
			entity.setConnectivityPointEMSRDFID(connectivityPointEMSRDFID);

			// 是否站内设备
			// String isStation = rowData.get(6);
			// entity.setIsStation(isStation);

			// 添加到数据列表
			// if (ObjUtil.equals(isStation, "非站内设备"))
			dataList.add(entity);
		}

		@Override
		public void doAfterAllAnalysed(AnalysisContext context) {
			// 标记第一个sheet处理完成
			isFirstSheet = false;
			log.info("Sheet {} 处理完成，共{}条数据", context.readSheetHolder().getSheetName(), dataList.size());
		}

		public List<ConnectivityForTmp> getDataList() {
			return dataList;
		}
	}

	public void selectConnectivityByDeviceTypeToNewConnectivityB() {
		log.info("开始执行selectConnectivityByDeviceTypeToNewConnectivityB方法...");

		// 使用分页查询处理大量数据
		int pageSize = 50000; // 每页查询10000条数据
		int currentPage = 1;
		boolean hasMoreData = true;

		List<NewConnectivitynodeB> newConnectivitynodeBList = new ArrayList<>();

		while (hasMoreData) {
			log.info("查询第{}页数据，每页{}条", currentPage, pageSize);

			// 分页查询connectivitynodeB表数据
			Page<ConnectivitynodeB> page = new Page<>(
					currentPage, pageSize);
			Page<ConnectivitynodeB> resultPage = connectivitynodeBService
					.page(page);

			List<ConnectivitynodeB> connectivitynodeBList = resultPage.getRecords();
			log.info("第{}页查询到connectivitynodeB数据: {}条", currentPage, connectivitynodeBList.size());

			// 检查是否还有更多数据
			if (connectivitynodeBList.isEmpty()) {
				hasMoreData = false;
				log.info("已查询完所有数据");
				break;
			}

			// 按设备类型分组
			Map<Long, List<ConnectivitynodeB>> deviceTypeMap = new HashMap<>();
			for (ConnectivitynodeB item : connectivitynodeBList) {
				long deviceType = item.getEquipment_ID().longValueExact() >>> 48;
				if (!deviceTypeMap.containsKey(deviceType)) {
					deviceTypeMap.put(deviceType, new ArrayList<>());
				}
				deviceTypeMap.get(deviceType).add(item);
			}

			log.info("第{}页按设备类型分组完成，共{}个设备类型", currentPage, deviceTypeMap.size());

			// 批量查询每个设备类型的substation_id
			for (Map.Entry<Long, List<ConnectivitynodeB>> entry : deviceTypeMap.entrySet()) {
				long deviceType = entry.getKey();
				List<ConnectivitynodeB> items = entry.getValue();

				log.info("处理设备类型 {}，共{}条数据", deviceType, items.size());

				// 收集设备ID列表
				List<BigDecimal> equipmentIDList = new ArrayList<>();
				for (ConnectivitynodeB item : items) {
					equipmentIDList.add(item.getEquipment_ID());
				}

				Map<BigDecimal, Map<String, Object>> substationIdMap = connectivitynodeBMapper
						.selectBatchForSubstationIdsByequipmentID(equipmentIDList, deviceType,
								new BigDecimal("3096224844480513"));

				if (substationIdMap.isEmpty()) {
					continue;
				}
				log.info("批量查询完成，返回{}条结果", substationIdMap.size());

				// 处理查询结果
				for (ConnectivitynodeB item : items) {
					Map<String, Object> substationIdMapItem = substationIdMap.get(item.getEquipment_ID());
					if (substationIdMapItem == null) {
						continue;
					}
					BigDecimal substationID = (BigDecimal) substationIdMapItem.get("value");

					if (substationID != null && !BigDecimal.ZERO.equals(substationID)) {
						NewConnectivitynodeB newConnectivitynodeB = new NewConnectivitynodeB();
						newConnectivitynodeB.setEquipment_ID(item.getEquipment_ID());
						newConnectivitynodeB.setSubstation_ID(substationID);
						newConnectivitynodeB.setID(item.getID());
						newConnectivitynodeB.setMultSrcFlag(item.getMultSrcFlag());
						newConnectivitynodeB.setAliasName(item.getAliasName());
						newConnectivitynodeB.setSequence(item.getSequence());
						newConnectivitynodeB.setDeleteFlag(item.getDeleteFlag());
						newConnectivitynodeB.setPhaseCode(item.getPhaseCode());
						newConnectivitynodeB.setVoltageLevel_ID(item.getVoltageLevel_ID());
						newConnectivitynodeB.setTerminalRdfID(item.getTerminalRdfID());
						newConnectivitynodeB.setName(item.getName());

						newConnectivitynodeBList.add(newConnectivitynodeB);
					} else if (BigDecimal.ZERO.equals(substationID)) {
						continue;
					} else if (substationID == null) {
						continue;
					}
					if (newConnectivitynodeBList.size() > 1000) {
						try {
							newconnectivitynodeBService.saveBatch(newConnectivitynodeBList);
						} catch (DuplicateKeyException e) {
							log.error("写入 newConnectivitynodeB 表时发生主键冲突，已跳过 {} 条数据", newConnectivitynodeBList.size());
							continue;
						}

						log.info("已写入 {} 条数据到 newConnectivitynodeB 表", newConnectivitynodeBList.size());

						newConnectivitynodeBList.clear();
					}
				}
			}

			// 增加页码
			currentPage++;
		}

		// 处理剩余数据
		if (!newConnectivitynodeBList.isEmpty()) {
			newconnectivitynodeBService.saveBatch(newConnectivitynodeBList);
			log.info("已写入剩余 {} 条数据到 newConnectivitynodeB 表", newConnectivitynodeBList.size());
			newConnectivitynodeBList.clear();
		}

		log.info("selectConnectivityToNewConnectivity方法执行完成");
	}

	public void selectConnectivityForAll1() {
		log.info("开始执行selectConnectivityForAll方法...");

		List<ConnectivityForTmp> uniqueList = connectivityForTmpService.list();
		log.info("查询到connectivityForTmp数据: {}条", uniqueList.size());

		List<NewConnectivitynodeB> newConnectivitynodeBList = new ArrayList<>();
		// 使用Set来跟踪已经存在的双主键组合，防止重复
		Set<String> primaryKeySet = new HashSet<>();

		// 去重处理 - 使用与数据库一致的分隔符
		Set<String> keySet = new HashSet<>();
		List<ConnectivityForTmp> connectivityForTmpList = new ArrayList<>();

		for (ConnectivityForTmp item : uniqueList) {
			if (item.getDeviceGISRDFID() == null)
				item.setDeviceGISRDFID(item.getDeviceEMSRDFID());

			if (item.getConnectivityPointGISRDFID() == null)
				item.setConnectivityPointGISRDFID(item.getConnectivityPointEMSRDFID());

			if (item.getDeviceGISRDFID() != null && item.getConnectivityPointEndID() != null) {
				// 使用与数据库一致的分隔符
				String key = item.getDeviceGISRDFID().toString() + "-" + item.getConnectivityPointEndID().toString();
				if (!keySet.contains(key)) {
					keySet.add(key);
					connectivityForTmpList.add(item);
				} else {
					log.info(item.toString());
					log.warn("发现重复组合键: {}-{}, 已跳过", item.getDeviceGISRDFID(), item.getConnectivityPointEndID());
				}
			}

		}

		// 预加载所有需要的设备ID映射，避免在循环中多次查询数据库
		Set<String> allDeviceGISRDFIDs = new HashSet<>();
		Set<String> allConnectivityPointGISRDFIDs = new HashSet<>();
		
		for (ConnectivityForTmp tmp : connectivityForTmpList) {
			if (tmp.getDeviceGISRDFID() != null) {
				allDeviceGISRDFIDs.add(tmp.getDeviceGISRDFID());
			}
			if (tmp.getConnectivityPointGISRDFID() != null) {
				allConnectivityPointGISRDFIDs.add(tmp.getConnectivityPointGISRDFID());
			}
		}
		
		// 批量查询设备和连接点映射，只有在集合不为空时才执行查询
		Map<String, RdflistB> rdflistMap = new HashMap<>();
		if (!allDeviceGISRDFIDs.isEmpty()) {
			List<RdflistB> rdflistBList = rdflistBService.list(
				new LambdaQueryWrapper<RdflistB>().in(RdflistB::getGISRdfID, allDeviceGISRDFIDs)
			);
			rdflistMap = rdflistBList.stream()
				.collect(Collectors.toMap(RdflistB::getGISRdfID, Function.identity(), (existing, replacement) -> existing));
		}
		
		Map<String, NodeRdflistB> nodeRdflistMap = new HashMap<>();
		if (!allConnectivityPointGISRDFIDs.isEmpty()) {
			List<NodeRdflistB> nodeRdflistBList = nodeRdflistBService.list(
				new LambdaQueryWrapper<NodeRdflistB>().in(NodeRdflistB::getGISRdfID, allConnectivityPointGISRDFIDs)
			);
			nodeRdflistMap = nodeRdflistBList.stream()
				.collect(Collectors.toMap(NodeRdflistB::getGISRdfID, Function.identity(), (existing, replacement) -> existing));
		}

		// 遍历connectivityForTmpList
		log.info("开始处理connectivityForTmpList，共{}条数据", connectivityForTmpList.size());

		int processedCount = 0;
		for (ConnectivityForTmp connectivityForTmp : connectivityForTmpList) {
			processedCount++;
			if (processedCount % 5000 == 0) {
				log.info("已处理{}条数据", processedCount);
			}

			String deviceGISRDFID = connectivityForTmp.getDeviceGISRDFID();
			String connectivityPointGISRDFID = connectivityForTmp.getConnectivityPointGISRDFID();

			NewConnectivitynodeB newConnectivitynodeB = new NewConnectivitynodeB();

			// 从预加载的映射中获取设备ID
			RdflistB rdflistB = rdflistMap.get(deviceGISRDFID);
			if (ObjUtil.isNotNull(rdflistB) && !StrUtil.isEmptyIfStr(rdflistB.getDeviceID())) {
				newConnectivitynodeB.setEquipment_ID(rdflistB.getDeviceID());
			}

			// 赋值连接点端号
			newConnectivitynodeB.setSequence(connectivityForTmp.getConnectivityPointEndID());

			// 赋值多电源标示
			newConnectivitynodeB.setMultSrcFlag(connectivityForTmp.getMultiPowerFlag());

			// 赋值substation 为0
			newConnectivitynodeB.setSubstation_ID(new BigDecimal(0));

			// 从预加载的映射中获取连接点ID
			NodeRdflistB nodeRdflistB = nodeRdflistMap.get(connectivityPointGISRDFID);
			if (ObjUtil.isNotNull(nodeRdflistB) && !StrUtil.isEmptyIfStr(nodeRdflistB.getDeviceID())) {
				newConnectivitynodeB.setID(nodeRdflistB.getDeviceID());
			}

			if (ObjUtil.isNotNull(newConnectivitynodeB.getEquipment_ID())
					&& ObjUtil.isNotNull(newConnectivitynodeB.getID())) {
				// 生成双主键的唯一标识
				String primaryKey = newConnectivitynodeB.getEquipment_ID().toString() + "_" + newConnectivitynodeB.getSequence().toString();
				
				// 检查双主键是否已经存在
				if (primaryKeySet.contains(primaryKey)) {
					continue; // 跳过重复的双主键组合
				}
				
				primaryKeySet.add(primaryKey);
				newConnectivitynodeBList.add(newConnectivitynodeB);
			}

			// 当列表达到50000条时进行处理
			if (newConnectivitynodeBList.size() >= 50000) {
				// 去重处理 - 使用与数据库一致的分隔符
				// Set<String> keySet = new HashSet<>();
				// List<NewConnectivitynodeB> uniqueList = new ArrayList<>();

				// for (NewConnectivitynodeB item : newConnectivitynodeBList) {
				// if (item.getEquipment_ID() != null && item.getSequence() != null) {
				// // 使用与数据库一致的分隔符
				// String key = item.getEquipment_ID().toString() + "-" +
				// item.getSequence().toString();
				// if (!keySet.contains(key)) {
				// keySet.add(key);
				// uniqueList.add(item);
				// } else {
				// log.info(item.toString());
				// log.warn("发现重复组合键: {}-{}, 已跳过", item.getEquipment_ID(), item.getSequence());
				// }
				// }
				// }

				// 保存结果
				log.info("开始保存newConnectivitynodeBList，共{}条数据", newConnectivitynodeBList.size());
				if (!newConnectivitynodeBList.isEmpty()) {
					newconnectivitynodeBService.saveBatch(newConnectivitynodeBList);
				}

				// 清空列表和主键集合
				newConnectivitynodeBList.clear();
				primaryKeySet.clear();
			}
		}

		// 处理剩余数据
		if (!newConnectivitynodeBList.isEmpty()) {
			// 保存结果
			log.info("开始保存剩余数据，共{}条数据", newConnectivitynodeBList.size());
			if (!newConnectivitynodeBList.isEmpty()) {
				newconnectivitynodeBService.saveBatch(newConnectivitynodeBList);
			}
		}

		log.info("selectConnectivityForAll方法执行完成");
	}

	public void selectConnectivityForAll() {
		log.info("开始执行selectConnectivityForAll方法...");

		List<NewConnectivitynodeB> newConnectivitynodeBList = new ArrayList<>();
		// 使用Set来跟踪已经存在的双主键组合，防止重复
		Set<String> primaryKeySet = new HashSet<>();

		// 使用分页查询处理大量数据
		int pageSize = 50000; // 每页查询10000条数据
		int currentPage = 1;
		boolean hasMoreData = true;


		while (hasMoreData) {
			// 分页查询connectivitynodeB表数据
			Page<ConnectivityForTmp> page = new Page<>(
					currentPage, pageSize);
			Page<ConnectivityForTmp> resultPage = connectivityForTmpService
					.page(page);

			List<ConnectivityForTmp> records = resultPage.getRecords();

			log.info("第{}页查询到connectivityForTmp数据: {}条", currentPage, records.size());
			if (records.isEmpty()) {
				hasMoreData = false;
			} else {
				currentPage++;
			}


			// 预加载所有需要的设备ID映射，避免在循环中多次查询数据库
			Set<String> allDeviceGISRDFIDs = new HashSet<>();
			Set<String> allConnectivityPointGISRDFIDs = new HashSet<>();
			
			for (ConnectivityForTmp tmp : records) {
				if (tmp.getDeviceGISRDFID() != null) {
					allDeviceGISRDFIDs.add(tmp.getDeviceGISRDFID());
				}
				if (tmp.getConnectivityPointGISRDFID() != null) {
					allConnectivityPointGISRDFIDs.add(tmp.getConnectivityPointGISRDFID());
				}
			}
			
			// 批量查询设备和连接点映射，只有在集合不为空时才执行查询
			Map<String, RdflistB> rdflistMap = new HashMap<>();
			if (!allDeviceGISRDFIDs.isEmpty()) {
				List<RdflistB> rdflistBList = rdflistBService.list(
					new LambdaQueryWrapper<RdflistB>().in(RdflistB::getGISRdfID, allDeviceGISRDFIDs)
				);
				rdflistMap = rdflistBList.stream()
					.collect(Collectors.toMap(RdflistB::getGISRdfID, Function.identity(), (existing, replacement) -> existing));
			}
			
			Map<String, NodeRdflistB> nodeRdflistMap = new HashMap<>();
			if (!allConnectivityPointGISRDFIDs.isEmpty()) {
				List<NodeRdflistB> nodeRdflistBList = nodeRdflistBService.list(
					new LambdaQueryWrapper<NodeRdflistB>().in(NodeRdflistB::getGISRdfID, allConnectivityPointGISRDFIDs)
				);
				nodeRdflistMap = nodeRdflistBList.stream()
					.collect(Collectors.toMap(NodeRdflistB::getGISRdfID, Function.identity(), (existing, replacement) -> existing));
			}

			// 遍历connectivityForTmpList
			log.info("开始处理connectivityForTmpList，共{}条数据", records.size());

			int processedCount = 0;
			for (ConnectivityForTmp connectivityForTmp : records) {
				processedCount++;
				if (processedCount % 5000 == 0) {
					log.info("已处理{}条数据", processedCount);
				}

				String deviceGISRDFID = connectivityForTmp.getDeviceGISRDFID();
				String deviceEMSRDFID = connectivityForTmp.getDeviceEMSRDFID();
				String connectivityPointGISRDFID = connectivityForTmp.getConnectivityPointGISRDFID();
				String connectivityPointEMSRDFID = connectivityForTmp.getConnectivityPointEMSRDFID();

				String newDeviceGISRDFID = deviceGISRDFID;
				String newConnectivityPointGISRDFID = connectivityPointGISRDFID;
				String newDeviceEMSRDFID = deviceEMSRDFID;
				String newConnectivityPointEMSRDFID = connectivityPointEMSRDFID;

				// 识别字符类型并且剔除掉所有的非数字字符
				if (!StrUtil.isEmptyIfStr(deviceGISRDFID)) {
					newDeviceGISRDFID = deviceGISRDFID.replaceAll("[^0-9]", "");
					newDeviceGISRDFID += deviceGISRDFID.length();
				}
				if (!StrUtil.isEmptyIfStr(connectivityPointGISRDFID)) {
					newConnectivityPointGISRDFID = connectivityPointGISRDFID.replaceAll("[^0-9]", "");
					newConnectivityPointGISRDFID += connectivityPointGISRDFID.length();
				}
				if (!StrUtil.isEmptyIfStr(deviceEMSRDFID)) {
					newDeviceEMSRDFID = deviceEMSRDFID.replaceAll("[^0-9]", "");
					newDeviceEMSRDFID += deviceEMSRDFID.length();
				}
				if (!StrUtil.isEmptyIfStr(connectivityPointEMSRDFID)) {
					newConnectivityPointEMSRDFID = connectivityPointEMSRDFID.replaceAll("[^0-9]", "");
					newConnectivityPointEMSRDFID += connectivityPointEMSRDFID.length();
				}

				NewConnectivitynodeB newConnectivitynodeB = new NewConnectivitynodeB();

				// 从预加载的映射中获取设备ID
				RdflistB rdflistB = rdflistMap.get(deviceGISRDFID);
				if (ObjUtil.isNotNull(rdflistB) && !StrUtil.isEmptyIfStr(rdflistB.getDeviceID())) {
					newConnectivitynodeB.setEquipment_ID(rdflistB.getDeviceID());
				} else if (!StrUtil.isEmptyIfStr(newDeviceGISRDFID)) {
					try {
						BigDecimal equipmentID = new BigDecimal(newDeviceGISRDFID);
						newConnectivitynodeB.setEquipment_ID(equipmentID);
					} catch (NumberFormatException e) {
						log.warn("设备GISRDFID格式错误: {}", newDeviceGISRDFID);
					}
				} else if (!StrUtil.isEmptyIfStr(newDeviceEMSRDFID)) {
					try {
						BigDecimal equipmentID = new BigDecimal(newDeviceEMSRDFID);
						newConnectivitynodeB.setEquipment_ID(equipmentID);
					} catch (NumberFormatException e) {
						log.warn("设备EMSRDFID格式错误: {}", newDeviceEMSRDFID);
					}
				}

				// 赋值连接点端号
				newConnectivitynodeB.setSequence(connectivityForTmp.getConnectivityPointEndID());

				// 赋值多电源标示
				newConnectivitynodeB.setMultSrcFlag(connectivityForTmp.getMultiPowerFlag());

				// 从预加载的映射中获取连接点ID
				NodeRdflistB nodeRdflistB = nodeRdflistMap.get(connectivityPointGISRDFID);
				if (ObjUtil.isNotNull(nodeRdflistB) && !StrUtil.isEmptyIfStr(nodeRdflistB.getDeviceID())) {
					newConnectivitynodeB.setID(nodeRdflistB.getDeviceID());
				} else if (!StrUtil.isEmptyIfStr(newConnectivityPointGISRDFID)) {
					try {
						BigDecimal id = new BigDecimal(newConnectivityPointGISRDFID);
						newConnectivitynodeB.setID(id);
					} catch (NumberFormatException e) {
						log.warn("连接点GISRDFID格式错误: {}", newConnectivityPointGISRDFID);
					}
				} else if (!StrUtil.isEmptyIfStr(newConnectivityPointEMSRDFID)) {
					try {
						BigDecimal id = new BigDecimal(newConnectivityPointEMSRDFID);
						newConnectivitynodeB.setID(id);
					} catch (NumberFormatException e) {
						log.warn("连接点EMSRDFID格式错误: {}", newConnectivityPointEMSRDFID);
					}
				}

				if (newConnectivitynodeB.getEquipment_ID() == null || newConnectivitynodeB.getSequence() == null) {
					continue;
				}
				
				// 生成双主键的唯一标识
				String primaryKey = newConnectivitynodeB.getEquipment_ID().toString() + "_" + newConnectivitynodeB.getSequence().toString();
				
				// 检查双主键是否已经存在
				if (primaryKeySet.contains(primaryKey)) {
					continue; // 跳过重复的双主键组合
				}
				
				primaryKeySet.add(primaryKey);
				newConnectivitynodeBList.add(newConnectivitynodeB);


//				newconnectivitynodeBService.saveOrUpdate(newConnectivitynodeB,
//						new LambdaUpdateWrapper<NewConnectivitynodeB>()
//								.eq(NewConnectivitynodeB::getEquipment_ID, newConnectivitynodeB.getEquipment_ID())
//								.eq(NewConnectivitynodeB::getSequence, newConnectivitynodeB.getSequence()));

				// 当列表达到50000条时进行处理
				if (newConnectivitynodeBList.size() % 50000 == 0) {
					//保存结果

					if (!newConnectivitynodeBList.isEmpty()) {
//						// 判断是否有双主键重复
//						newconnectivitynodeBService.saveOrUpdateBatch(newConnectivitynodeBList);
						newconnectivitynodeBMapper.batchReplaceInsert(newConnectivitynodeBList);

						log.info("已写入{}条数据到connectivityfortmp", newConnectivitynodeBList.size());
					}

					// 清空列表和主键集合
					newConnectivitynodeBList.clear();
					primaryKeySet.clear();
				}
			}
		}

		// 处理剩余数据
		if (!newConnectivitynodeBList.isEmpty()) {
			// 保存结果
			log.info("开始保存剩余数据，共{}条数据（去重后：{}条）", newConnectivitynodeBList.size(), newConnectivitynodeBList.size());
			if (!newConnectivitynodeBList.isEmpty()) {
				//newconnectivitynodeBService.saveOrUpdateBatch(newConnectivitynodeBList);
			}
		}

		log.info("selectConnectivityForAll方法执行完成");
	}
}
