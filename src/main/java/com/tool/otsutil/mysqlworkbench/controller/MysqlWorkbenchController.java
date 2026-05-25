package com.tool.otsutil.mysqlworkbench.controller;

import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.model.vo.auth.LoginUserView;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlExportCreateRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlHistoryQueryRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowDeleteRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowInsertRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowUpdateRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlSavedQuerySaveRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlSqlExecuteRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableDesignRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableQueryRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlDesignPreviewView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlExportJobView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryDetailView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryPageView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSavedQueryView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlBatchResultView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlExecutionView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableDataPageView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableMetadataView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableNamePageView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTreeNodeView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlWriteResultView;
import com.tool.otsutil.mysqlworkbench.service.MysqlExportJobService;
import com.tool.otsutil.mysqlworkbench.service.MysqlSavedQueryService;
import com.tool.otsutil.mysqlworkbench.service.MysqlSqlExecutionService;
import com.tool.otsutil.mysqlworkbench.service.MysqlWorkbenchService;
import com.tool.otsutil.service.auth.AuthService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

@RestController
@RequestMapping("/mysql-workbench")
public class MysqlWorkbenchController {

    private final MysqlWorkbenchService mysqlWorkbenchService;

    private final MysqlSavedQueryService mysqlSavedQueryService;

    private final MysqlExportJobService mysqlExportJobService;

    private final MysqlSqlExecutionService mysqlSqlExecutionService;

    private final AuthService authService;

    public MysqlWorkbenchController(MysqlWorkbenchService mysqlWorkbenchService,
                                    MysqlSavedQueryService mysqlSavedQueryService,
                                    MysqlExportJobService mysqlExportJobService,
                                    MysqlSqlExecutionService mysqlSqlExecutionService,
                                    AuthService authService) {
        this.mysqlWorkbenchService = mysqlWorkbenchService;
        this.mysqlSavedQueryService = mysqlSavedQueryService;
        this.mysqlExportJobService = mysqlExportJobService;
        this.mysqlSqlExecutionService = mysqlSqlExecutionService;
        this.authService = authService;
    }

    @GetMapping("/schemas")
    public ResponseResult<List<String>> listSchemas(@RequestParam(defaultValue = "false") boolean includeSystemSchemas) {
        return ResponseResult.okResult(mysqlWorkbenchService.listSchemas(includeSystemSchemas));
    }

    @GetMapping("/schemas/{schema}/tables")
    public ResponseResult<MysqlTableNamePageView> listTables(@PathVariable String schema,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "100") int pageSize,
                                                             @RequestParam(required = false) String keyword) {
        return ResponseResult.okResult(mysqlWorkbenchService.listTablePage(schema, page, pageSize, keyword));
    }

    @GetMapping("/schemas/{schema}/columns")
    public ResponseResult<Map<String, List<String>>> listColumns(@PathVariable String schema,
                                                                 @RequestParam(defaultValue = "") String tables) {
        return ResponseResult.okResult(mysqlWorkbenchService.listTableColumns(schema, parseCommaSeparatedList(tables)));
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

    @PostMapping("/sql/executions")
    public ResponseResult<MysqlSqlExecutionView> createSqlExecution(@RequestBody MysqlSqlExecuteRequest request,
                                                                    HttpServletRequest servletRequest) {
        return ResponseResult.okResult(mysqlSqlExecutionService.createExecution(request, currentUsername(servletRequest)));
    }

    @GetMapping("/sql/executions/{executionId}")
    public ResponseResult<MysqlSqlExecutionView> getSqlExecution(@PathVariable String executionId) {
        return ResponseResult.okResult(mysqlSqlExecutionService.getExecution(executionId));
    }

    @PostMapping("/sql/executions/{executionId}/cancel")
    public ResponseResult<MysqlSqlExecutionView> cancelSqlExecution(@PathVariable String executionId) {
        return ResponseResult.okResult(mysqlSqlExecutionService.cancelExecution(executionId));
    }

    @GetMapping("/history")
    public ResponseResult<MysqlHistoryPageView> listHistory(MysqlHistoryQueryRequest request) {
        return ResponseResult.okResult(mysqlWorkbenchService.listHistory(request));
    }

    @GetMapping("/history/{batchId}")
    public ResponseResult<MysqlHistoryDetailView> getHistoryDetail(@PathVariable long batchId) {
        return ResponseResult.okResult(mysqlWorkbenchService.getHistoryDetail(batchId));
    }

    @GetMapping("/queries")
    public ResponseResult<List<MysqlSavedQueryView>> listSavedQueries() {
        return ResponseResult.okResult(mysqlSavedQueryService.listQueries());
    }

    @PostMapping("/queries")
    public ResponseResult<MysqlSavedQueryView> saveQuery(@RequestBody MysqlSavedQuerySaveRequest request) {
        return ResponseResult.okResult(mysqlSavedQueryService.saveQuery(request));
    }

    @DeleteMapping("/queries/{queryId}")
    public ResponseResult<Void> deleteQuery(@PathVariable long queryId) {
        mysqlSavedQueryService.deleteQuery(queryId);
        return ResponseResult.okResult(null);
    }

    @PostMapping("/exports")
    public ResponseResult<MysqlExportJobView> createExport(@RequestBody MysqlExportCreateRequest request,
                                                           HttpServletRequest servletRequest) {
        return ResponseResult.okResult(mysqlExportJobService.createJob(request, currentUsername(servletRequest)));
    }

    @GetMapping("/exports")
    public ResponseResult<List<MysqlExportJobView>> listExports(@RequestParam(required = false) Integer page,
                                                                @RequestParam(required = false) Integer pageSize) {
        return ResponseResult.okResult(mysqlExportJobService.listJobs(page, pageSize));
    }

    @GetMapping("/exports/{jobId}")
    public ResponseResult<MysqlExportJobView> getExport(@PathVariable long jobId) {
        return ResponseResult.okResult(mysqlExportJobService.getJob(jobId));
    }

    @GetMapping("/exports/{jobId}/download")
    public ResponseEntity<Resource> downloadExport(@PathVariable long jobId) throws Exception {
        Path file = mysqlExportJobService.getDownloadFile(jobId);
        String fileName = file.getFileName().toString();
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        MediaType mediaType = resolveExportMediaType(fileName);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(new FileSystemResource(file.toFile()));
    }

    private String currentUsername(HttpServletRequest servletRequest) {
        LoginUserView currentUser = authService.getCurrentUser(servletRequest.getSession(false));
        return currentUser == null ? "unknown" : currentUser.getUsername();
    }

    private MediaType resolveExportMediaType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".csv")) {
            return new MediaType("text", "csv", StandardCharsets.UTF_8);
        }
        if (lower.endsWith(".xlsx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        if (lower.endsWith(".sql")) {
            return new MediaType("text", "sql", StandardCharsets.UTF_8);
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private List<String> parseCommaSeparatedList(String value) {
        return Arrays.stream((value == null ? "" : value).split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }
}
