package com.tool.otsutil.service.InspectionImpl;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.TableStoreException;
import com.alicloud.openservices.tablestore.model.*;
import com.tool.otsutil.config.OtsProperties;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.exception.ExceptionCatch;
import com.tool.otsutil.model.common.*;
import com.tool.otsutil.model.dto.ots.CurveSaveResult;
import com.tool.otsutil.model.dto.ots.CurveTemplateDay;
import com.tool.otsutil.model.dto.ots.CurveTemplateType;
import com.tool.otsutil.service.ots.CurveTemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class OtsService {

    private final SyncClient otsClient;
    private static final String TABLE_NAME = "dyyc";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final DateTimeFormatter OTS_KEY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter OTS_COLUMN_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal CURVE_PERCENTAGE = new BigDecimal("0.05");

    @Autowired
    private OtsProperties otsProperties;

    @Autowired
    private ExceptionCatch exceptionCatch;

    public OtsService(SyncClient otsClient) {
        this.otsClient = otsClient;
    }

    @Autowired
    private GlobalOTSValue globalOTSValue;

    @Autowired
    private CurveTemplateLoader curveTemplateLoader;

    public static String getFifteenMinutes(String inputTime, boolean isNextMinute) throws ParseException {
        // 解析输入字符串为日期对象
        Date date = DATE_FORMAT.parse(inputTime);

        // 使用Calendar类增加15分钟
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        if (isNextMinute) {
            calendar.add(Calendar.MINUTE, 15);
        } else {
            calendar.add(Calendar.MINUTE, -15);
        }

        // 格式化新的日期对象为字符串

        return DATE_FORMAT.format(calendar.getTime());
    }

    //
    public boolean checkKeyExists(String key) {
        SingleRowQueryCriteria singleRowQueryCriteria = this.getSingleRowQueryCriteria(key);

        GetRowRequest getRowRequest = new GetRowRequest(singleRowQueryCriteria);
        try {
            GetRowResponse getRowResponse = otsClient.getRow(getRowRequest);
            return getRowResponse.getRow() != null;
        } catch (TableStoreException e) {
            // Handle the exception as per your requirement
            e.printStackTrace();
            return false;
        }
    }

    public SingleRowQueryCriteria getSingleRowQueryCriteria(String key) {
        PrimaryKey primaryKey = PrimaryKeyBuilder.createPrimaryKeyBuilder()
                .addPrimaryKeyColumn(otsProperties.getKey(), PrimaryKeyValue.fromString(key))
                .build();
        SingleRowQueryCriteria singleRowQueryCriteria = new SingleRowQueryCriteria(TABLE_NAME, primaryKey);

        singleRowQueryCriteria.setMaxVersions(1);
        return singleRowQueryCriteria;
    }

    public BigDecimal getOtsValue(String key) {
        SingleRowQueryCriteria singleRowQueryCriteria = this.getSingleRowQueryCriteria(key);
        Row row;
        try {
            GetRowRequest getRowRequest = new GetRowRequest(singleRowQueryCriteria);

            GetRowResponse getRowResponse = otsClient.getRow(getRowRequest);
            row = getRowResponse.getRow();
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }

        if (row != null) {
            ColumnValue columnValue = row.getLatestColumn("value").getValue();
            return columnValue != null ? new BigDecimal(columnValue.asString()) : null;
        }
        return null;
    }

    // 设置全局值
    public void setGlobalValue(String key, BigDecimal value) {
        String otsKey = globalOTSValue.getKey(key);

        PrimaryKey primaryKey = PrimaryKeyBuilder.createPrimaryKeyBuilder()
                .addPrimaryKeyColumn(otsProperties.getKey(), PrimaryKeyValue.fromString(otsKey))
                .build();
        RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKey);
        rowPutChange.addColumn("value", ColumnValue.fromString(value.toString()));

        try {
            otsClient.putRow(new PutRowRequest(rowPutChange));
        } catch (Exception e) {
            exceptionCatch.exception(new CustomException(AppHttpCodeEnum.SERVER_ERROR, "ots连接异常"));
            throw new RuntimeException(e);
        }
    }

    //写入单条到ots
    public void writeData(String key, BigDecimal value, String time) {
        PrimaryKey primaryKey = PrimaryKeyBuilder.createPrimaryKeyBuilder()
                .addPrimaryKeyColumn(otsProperties.getKey(), PrimaryKeyValue.fromString(key))
                .build();
        RowPutChange rowPutChange = new RowPutChange(TABLE_NAME, primaryKey);


        rowPutChange.addColumn("time", ColumnValue.fromString(time));
        rowPutChange.addColumn("value", ColumnValue.fromString(value.toString()));
        otsClient.putRow(new PutRowRequest(rowPutChange));

        log.info("write data to ots, key:{}, value:{}, time:{}", key, value, time);
    }

    // 保存一天的断点数据
    public void saveOneDayData(String id, String day, BigDecimal percentage) throws ParseException {
        // 获取time当天从0点开始一直到当天结束的时间戳，每隔十五分钟一个，day 为 20241116，获取的为20241116000000-20241116234500
        long startTime = Long.parseLong(day + "000000");
        long endTime = Long.parseLong(day + "235500");

        boolean isFirstNull = false;

        while (startTime <= endTime) {
            String currentTime = getFifteenMinutes(String.valueOf(startTime), true);

            // 判断是否已经存在，不存在就写入
            BigDecimal value = getOtsValue(id + currentTime);
            if (isFirstNull && value != null) {
                isFirstNull = false;
            }

            //把currentTime格式化为yyyy-MM-dd HH:mm:ss
            String formatTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new SimpleDateFormat("yyyyMMddHHmmss").parse(currentTime));

            if (value == null) {
                log.info("时间点{}的值 is null", formatTime);

                // 判断是否连续为空，连续为空就设置为全局默认值的上下浮动百分之10的值
                if (isFirstNull) {
                    String defaultKey = globalOTSValue.getKey(id);
                    BigDecimal otsValue = new BigDecimal("0");

                    if (!defaultKey.isEmpty())
                        otsValue = getOtsValue(defaultKey);


                    otsValue = getRandomWithinRange(otsValue, percentage);

                    log.info("时间点{}的值设置为全局默认值{}", formatTime, otsValue);
                    writeData(id + currentTime, otsValue, formatTime);
                } else {
                    //获取上一个时间点的值
                    BigDecimal lastValue = getOtsValue(id + getFifteenMinutes(currentTime, false));
                    //上下浮动百分之10
                    lastValue = getRandomWithinRange(lastValue, percentage);

                    log.info("时间点{}的值设置为上一个时间点的值{}", formatTime, lastValue);
                    writeData(id + currentTime, lastValue, formatTime);
                }

                isFirstNull = true;
            }

            startTime = Long.parseLong(currentTime);
        }
    }

    public CurveSaveResult saveCurveData(String id, CurveTemplateType type, String day) {
        Map<CurveTemplateType, List<CurveTemplateDay>> templates = curveTemplateLoader.loadTemplates();
        List<CurveTemplateDay> templateDays = templates.get(type);
        if (templateDays == null || templateDays.isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "未找到曲线模板: " + type.name());
        }

        CurveTemplateDay templateDay = templateDays.get(new Random().nextInt(templateDays.size()));
        LocalDate targetDate = LocalDate.parse(day, DateTimeFormatter.BASIC_ISO_DATE);

        for (int index = 0; index < templateDay.getValues().size(); index++) {
            LocalDateTime targetDateTime = LocalDateTime.of(targetDate, templateDay.getTimes().get(index));
            BigDecimal currentValue = applyCurveVariation(templateDay.getValues().get(index));

            writeData(
                    id + targetDateTime.format(OTS_KEY_TIME_FORMAT),
                    currentValue,
                    targetDateTime.format(OTS_COLUMN_TIME_FORMAT)
            );
        }

        return new CurveSaveResult(type.name(), day, templateDay.getSourceDate().toString(), templateDay.getValues().size());
    }

    private BigDecimal applyCurveVariation(BigDecimal templateValue) {
        if (templateValue == null || templateValue.compareTo(BigDecimal.ZERO) == 0) {
            return templateValue == null ? BigDecimal.ZERO : templateValue;
        }
        return getRandomWithinRange(templateValue, CURVE_PERCENTAGE);
    }

    // 获取一个BigDecimal的随机值
    public static BigDecimal getRandomWithinRange(BigDecimal value, BigDecimal percentage) {
        Random random = new Random();

        // 生成一个在 [-percentage, +percentage] 范围内的随机浮动因子
        BigDecimal randomFactor = BigDecimal.valueOf(random.nextDouble())
                .multiply(percentage.multiply(BigDecimal.valueOf(2)))
                .subtract(percentage);

        // 计算新的值
        BigDecimal factor = BigDecimal.ONE.add(randomFactor);
        BigDecimal newValue = value.multiply(factor);

        log.info("原始值：{}, 浮动因子：{}, 新值：{}", value, randomFactor, newValue);

        // 保留原始值的小数位数
        int scale = value.scale();
        return newValue.setScale(scale, RoundingMode.HALF_UP);
    }

    // 获取原始值的小数位数
    public static int getDecimalPlaces(double value) {
        String text = Double.toString(value);
        int index = text.indexOf('.');
        return index < 0 ? 0 : text.length() - index - 1;
    }

    //获取key的measure
    public static measureType getMeasureType(String key) {
        //把key先转为BigDecimal类型，然后转换数字的二进制的32位到47提取为int
        int measure = Integer.parseInt(new BigDecimal(key).toBigInteger().toString(2).substring(32, 47), 2);

        return measureType.values()[measure];
    }

    // ---- OTS 查询工具方法 ----

    /**
     * 获取所有表名列表
     */
    public List<String> listTables() {
        ListTableResponse response = otsClient.listTable();
        return response.getTableNames();
    }

    /**
     * 获取表结构信息
     */
    public Map<String, Object> describeTable(String tableName) {
        DescribeTableRequest request = new DescribeTableRequest(tableName);
        DescribeTableResponse response = otsClient.describeTable(request);
        TableMeta tableMeta = response.getTableMeta();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableMeta.getTableName());

        // 主键列 - 使用 getPrimaryKeyMap() 获取
        List<Map<String, String>> primaryKeyColumns = new ArrayList<>();
        Map<String, PrimaryKeyType> pkMap = tableMeta.getPrimaryKeyMap();
        for (Map.Entry<String, PrimaryKeyType> entry : pkMap.entrySet()) {
            Map<String, String> col = new LinkedHashMap<>();
            col.put("name", entry.getKey());
            col.put("type", entry.getValue().name());
            primaryKeyColumns.add(col);
        }
        result.put("primaryKeyColumns", primaryKeyColumns);

        return result;
    }

    /**
     * 按 key 范围查询数据
     */
    public Map<String, Object> getRange(String tableName, String keyName, String startKey, String endKey, int limit) {
        RangeRowQueryCriteria criteria = new RangeRowQueryCriteria(tableName);

        // 设置起始主键
        PrimaryKeyBuilder startBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        startBuilder.addPrimaryKeyColumn(keyName, PrimaryKeyValue.fromString(startKey));
        criteria.setInclusiveStartPrimaryKey(startBuilder.build());

        // 设置结束主键
        PrimaryKeyBuilder endBuilder = PrimaryKeyBuilder.createPrimaryKeyBuilder();
        endBuilder.addPrimaryKeyColumn(keyName, PrimaryKeyValue.fromString(endKey));
        criteria.setExclusiveEndPrimaryKey(endBuilder.build());

        criteria.setMaxVersions(1);

        if (limit > 0) {
            criteria.setLimit(limit);
        }

        GetRangeResponse response = otsClient.getRange(new GetRangeRequest(criteria));
        List<Map<String, Object>> rows = new ArrayList<>();

        while (true) {
            for (Row row : response.getRows()) {
                Map<String, Object> rowData = new LinkedHashMap<>();
                rowData.put("key", row.getPrimaryKey().getPrimaryKeyColumn(0).getValue().asString());

                // 使用 getColumns() 获取所有列
                for (Column column : row.getColumns()) {
                    rowData.put(column.getName(), column.getValue().asString());
                }
                rows.add(rowData);
            }

            // 检查是否还有更多数据
            if (response.getNextStartPrimaryKey() == null) {
                break;
            }
            criteria.setInclusiveStartPrimaryKey(response.getNextStartPrimaryKey());
            response = otsClient.getRange(new GetRangeRequest(criteria));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableName);
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }
}
