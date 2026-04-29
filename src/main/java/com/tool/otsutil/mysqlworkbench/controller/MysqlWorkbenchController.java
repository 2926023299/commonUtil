package com.tool.otsutil.mysqlworkbench.controller;

import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.model.vo.auth.LoginUserView;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlHistoryQueryRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowDeleteRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowInsertRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowUpdateRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlSqlExecuteRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableDesignRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableQueryRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlDesignPreviewView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryDetailView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryPageView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlBatchResultView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableDataPageView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableMetadataView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTreeNodeView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlWriteResultView;
import com.tool.otsutil.mysqlworkbench.service.MysqlWorkbenchService;
import com.tool.otsutil.service.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/mysql-workbench")
public class MysqlWorkbenchController {

    private final MysqlWorkbenchService mysqlWorkbenchService;

    private final AuthService authService;

    public MysqlWorkbenchController(MysqlWorkbenchService mysqlWorkbenchService, AuthService authService) {
        this.mysqlWorkbenchService = mysqlWorkbenchService;
        this.authService = authService;
    }

    @GetMapping("/tree")
    public ResponseResult<List<MysqlTreeNodeView>> listTree(@RequestParam(defaultValue = "false") boolean includeSystemSchemas) {
        return ResponseResult.okResult(mysqlWorkbenchService.listTree(includeSystemSchemas));
    }

    @GetMapping("/table/metadata")
    public ResponseResult<MysqlTableMetadataView> getTableMetadata(@RequestParam String schema,
                                                                   @RequestParam String table) {
        return ResponseResult.okResult(mysqlWorkbenchService.getTableMetadata(schema, table));
    }

    @GetMapping("/table/ddl")
    public ResponseResult<String> getTableDdl(@RequestParam String schema,
                                              @RequestParam String table) {
        return ResponseResult.okResult(mysqlWorkbenchService.getTableDdl(schema, table));
    }

    @PostMapping("/table/data/query")
    public ResponseResult<MysqlTableDataPageView> queryTableData(@RequestBody MysqlTableQueryRequest request) {
        return ResponseResult.okResult(mysqlWorkbenchService.queryTableData(request));
    }

    @PostMapping("/table/data/insert")
    public ResponseResult<MysqlWriteResultView> insertRow(@RequestBody MysqlRowInsertRequest request) {
        return ResponseResult.okResult(mysqlWorkbenchService.insertRow(request));
    }

    @PutMapping("/table/data/update")
    public ResponseResult<MysqlWriteResultView> updateRow(@RequestBody MysqlRowUpdateRequest request) {
        return ResponseResult.okResult(mysqlWorkbenchService.updateRow(request));
    }

    @DeleteMapping("/table/data/delete")
    public ResponseResult<MysqlWriteResultView> deleteRow(@RequestBody MysqlRowDeleteRequest request) {
        return ResponseResult.okResult(mysqlWorkbenchService.deleteRow(request));
    }

    @PostMapping("/design/preview")
    public ResponseResult<MysqlDesignPreviewView> previewDesign(@RequestBody MysqlTableDesignRequest request) {
        return ResponseResult.okResult(mysqlWorkbenchService.previewDesign(request));
    }

    @PostMapping("/design/execute")
    public ResponseResult<MysqlSqlBatchResultView> executeDesign(@RequestBody MysqlTableDesignRequest request,
                                                                 HttpServletRequest servletRequest) {
        return ResponseResult.okResult(mysqlWorkbenchService.executeDesign(request, currentUsername(servletRequest)));
    }

    @PostMapping("/sql/execute")
    public ResponseResult<MysqlSqlBatchResultView> executeSql(@RequestBody MysqlSqlExecuteRequest request,
                                                              HttpServletRequest servletRequest) {
        return ResponseResult.okResult(mysqlWorkbenchService.executeSql(request, currentUsername(servletRequest)));
    }

    @GetMapping("/history")
    public ResponseResult<MysqlHistoryPageView> listHistory(MysqlHistoryQueryRequest request) {
        return ResponseResult.okResult(mysqlWorkbenchService.listHistory(request));
    }

    @GetMapping("/history/{batchId}")
    public ResponseResult<MysqlHistoryDetailView> getHistoryDetail(@PathVariable long batchId) {
        return ResponseResult.okResult(mysqlWorkbenchService.getHistoryDetail(batchId));
    }

    private String currentUsername(HttpServletRequest servletRequest) {
        LoginUserView currentUser = authService.getCurrentUser(servletRequest.getSession(false));
        return currentUser == null ? "unknown" : currentUser.getUsername();
    }
}
