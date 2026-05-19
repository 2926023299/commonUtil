package com.tool.otsutil.controller;

import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.model.dto.tools.OtsQueryRequest;
import com.tool.otsutil.model.dto.tools.TimestampConvertRequest;
import com.tool.otsutil.service.InspectionImpl.OtsService;
import com.tool.otsutil.service.ToolsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/tools")
public class ToolsController {

    @Autowired
    private ToolsService toolsService;

    @Autowired
    private OtsService otsService;

    // ---- 时间戳工具 ----

    @GetMapping("/timestamp/current")
    public ResponseResult currentTimestamp() {
        return ResponseResult.okResult(toolsService.currentTimestamp());
    }

    @PostMapping("/timestamp/convert")
    public ResponseResult convertTimestamp(@RequestBody TimestampConvertRequest request) {
        try {
            return ResponseResult.okResult(toolsService.convertTimestamp(request));
        } catch (Exception e) {
            log.error("时间戳转换失败", e);
            return ResponseResult.errorResult(500, "转换失败: " + e.getMessage());
        }
    }

    // ---- OTS 查询工具 ----

    @GetMapping("/ots/tables")
    public ResponseResult listOtsTables() {
        try {
            return ResponseResult.okResult(otsService.listTables());
        } catch (Exception e) {
            log.error("获取OTS表列表失败", e);
            return ResponseResult.errorResult(500, "获取表列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/ots/tables/{tableName}/schema")
    public ResponseResult describeOtsTable(@PathVariable String tableName) {
        try {
            return ResponseResult.okResult(otsService.describeTable(tableName));
        } catch (Exception e) {
            log.error("获取OTS表结构失败", e);
            return ResponseResult.errorResult(500, "获取表结构失败: " + e.getMessage());
        }
    }

    @PostMapping("/ots/query")
    public ResponseResult queryOtsRange(@RequestBody OtsQueryRequest request) {
        try {
            return ResponseResult.okResult(otsService.getRange(
                    request.getTableName(),
                    request.getKeyName(),
                    request.getStartKey(),
                    request.getEndKey(),
                    request.getLimit() != null ? request.getLimit() : 100
            ));
        } catch (Exception e) {
            log.error("OTS范围查询失败", e);
            return ResponseResult.errorResult(500, "查询失败: " + e.getMessage());
        }
    }
}
