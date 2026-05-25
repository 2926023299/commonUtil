package com.tool.otsutil.mysqlworkbench.service;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlSqlExecuteRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlBatchResultView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlExecutionView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MysqlSqlExecutionService {

    private static final int DEFAULT_EXECUTION_POOL_SIZE = 2;

    private final MysqlWorkbenchService mysqlWorkbenchService;

    private final Executor executor;

    private final ConcurrentMap<String, ExecutionTask> executions = new ConcurrentHashMap<String, ExecutionTask>();

    @Autowired
    public MysqlSqlExecutionService(MysqlWorkbenchService mysqlWorkbenchService) {
        this(mysqlWorkbenchService, Executors.newFixedThreadPool(DEFAULT_EXECUTION_POOL_SIZE));
    }

    MysqlSqlExecutionService(MysqlWorkbenchService mysqlWorkbenchService, Executor executor) {
        this.mysqlWorkbenchService = mysqlWorkbenchService;
        this.executor = executor;
    }

    public MysqlSqlExecutionView createExecution(MysqlSqlExecuteRequest request, String executedBy) {
        String id = UUID.randomUUID().toString();
        ExecutionTask task = new ExecutionTask(id, request, executedBy);
        executions.put(id, task);
        task.future = CompletableFuture.runAsync(() -> runExecution(task), executor);
        return toView(task);
    }

    public MysqlSqlExecutionView getExecution(String id) {
        return toView(resolveTask(id));
    }

    public MysqlSqlExecutionView cancelExecution(String id) {
        ExecutionTask task = resolveTask(id);
        if (isTerminalStatus(task.status)) {
            return toView(task);
        }

        task.control.cancel();
        if (!task.started.get()) {
            task.status = "CANCELED";
            task.message = "SQL 执行已取消";
            task.finishedAt = LocalDateTime.now();
            if (task.future != null) {
                task.future.cancel(false);
            }
        } else {
            task.status = "CANCELING";
            task.message = "正在停止 SQL 执行";
        }
        return toView(task);
    }

    private void runExecution(ExecutionTask task) {
        if (task.control.isCancelled()) {
            markCanceled(task);
            return;
        }

        task.started.set(true);
        task.startedAt = LocalDateTime.now();
        task.status = "RUNNING";
        task.message = "SQL 执行中";
        try {
            MysqlSqlBatchResultView result = mysqlWorkbenchService.executeSql(task.request, task.executedBy, task.control);
            task.result = result;
            task.status = result.getStatus() == null ? (Boolean.TRUE.equals(result.getSuccess()) ? "SUCCESS" : "FAILED") : result.getStatus();
            task.message = result.getMessage();
        } catch (CustomException e) {
            task.status = task.control.isCancelled() ? "CANCELED" : "FAILED";
            task.message = e.getMessage();
        } catch (RuntimeException e) {
            task.status = task.control.isCancelled() ? "CANCELED" : "FAILED";
            task.message = e.getMessage() == null ? "SQL 执行失败" : e.getMessage();
        } finally {
            if (task.control.isCancelled() && !"CANCELED".equals(task.status)) {
                task.status = "CANCELED";
                task.message = "SQL 执行已取消";
            }
            task.finishedAt = LocalDateTime.now();
        }
    }

    private void markCanceled(ExecutionTask task) {
        task.status = "CANCELED";
        task.message = "SQL 执行已取消";
        task.finishedAt = LocalDateTime.now();
    }

    private ExecutionTask resolveTask(String id) {
        ExecutionTask task = executions.get(id);
        if (task == null) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "SQL 执行任务不存在: " + id);
        }
        return task;
    }

    private boolean isTerminalStatus(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status) || "CANCELED".equals(status);
    }

    private MysqlSqlExecutionView toView(ExecutionTask task) {
        MysqlSqlExecutionView view = new MysqlSqlExecutionView();
        view.setId(task.id);
        view.setStatus(task.status);
        view.setMessage(task.message);
        view.setResult(task.result);
        view.setCreatedAt(task.createdAt);
        view.setStartedAt(task.startedAt);
        view.setFinishedAt(task.finishedAt);
        return view;
    }

    @PreDestroy
    public void shutdown() {
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdownNow();
        }
    }

    private static class ExecutionTask {

        private final String id;

        private final MysqlSqlExecuteRequest request;

        private final String executedBy;

        private final MysqlSqlExecutionControl control = new MysqlSqlExecutionControl();

        private final AtomicBoolean started = new AtomicBoolean(false);

        private final LocalDateTime createdAt = LocalDateTime.now();

        private volatile CompletableFuture<Void> future;

        private volatile String status = "RUNNING";

        private volatile String message = "SQL 执行中";

        private volatile MysqlSqlBatchResultView result;

        private volatile LocalDateTime startedAt;

        private volatile LocalDateTime finishedAt;

        private ExecutionTask(String id, MysqlSqlExecuteRequest request, String executedBy) {
            this.id = id;
            this.request = request;
            this.executedBy = executedBy;
        }
    }
}
