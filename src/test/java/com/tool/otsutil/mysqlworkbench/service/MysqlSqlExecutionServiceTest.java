package com.tool.otsutil.mysqlworkbench.service;

import com.tool.otsutil.mysqlworkbench.model.request.MysqlSqlExecuteRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlBatchResultView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlExecutionView;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MysqlSqlExecutionServiceTest {

    @Test
    void shouldCreateBeanThroughSpringConstructorInjection() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("mysqlWorkbenchService", mock(MysqlWorkbenchService.class));
        context.register(MysqlSqlExecutionService.class);
        context.refresh();

        MysqlSqlExecutionService executionService = context.getBean(MysqlSqlExecutionService.class);

        assertNotNull(executionService);
        context.close();
    }

    @Test
    void shouldRunSqlExecutionAndExposeCompletedResult() {
        MysqlWorkbenchService mysqlWorkbenchService = mock(MysqlWorkbenchService.class);
        MysqlSqlExecutionService executionService = new MysqlSqlExecutionService(mysqlWorkbenchService, Runnable::run);
        MysqlSqlExecuteRequest request = new MysqlSqlExecuteRequest();
        request.setSql("select 1");
        MysqlSqlBatchResultView resultView = new MysqlSqlBatchResultView();
        resultView.setStatus("SUCCESS");
        resultView.setSuccess(Boolean.TRUE);
        resultView.setMessage("执行成功，共 1 条语句");

        given(mysqlWorkbenchService.executeSql(eq(request), eq("tester"), any(MysqlSqlExecutionControl.class)))
                .willReturn(resultView);

        MysqlSqlExecutionView created = executionService.createExecution(request, "tester");
        MysqlSqlExecutionView completed = executionService.getExecution(created.getId());

        assertNotNull(created.getId());
        assertEquals("SUCCESS", completed.getStatus());
        assertEquals(resultView, completed.getResult());
    }

    @Test
    void shouldCancelPendingSqlExecution() {
        MysqlWorkbenchService mysqlWorkbenchService = mock(MysqlWorkbenchService.class);
        List<Runnable> pending = new ArrayList<Runnable>();
        Executor executor = pending::add;
        MysqlSqlExecutionService executionService = new MysqlSqlExecutionService(mysqlWorkbenchService, executor);
        MysqlSqlExecuteRequest request = new MysqlSqlExecuteRequest();
        request.setSql("select sleep(10)");

        MysqlSqlExecutionView created = executionService.createExecution(request, "tester");
        MysqlSqlExecutionView canceled = executionService.cancelExecution(created.getId());

        assertEquals(1, pending.size());
        assertEquals("CANCELED", canceled.getStatus());
        assertEquals("SQL 执行已取消", canceled.getMessage());
    }
}
