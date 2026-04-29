package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MysqlHistoryPageView {

    private Integer page;

    private Integer pageSize;

    private Long total;

    private List<MysqlHistoryBatchView> items = new ArrayList<MysqlHistoryBatchView>();
}
