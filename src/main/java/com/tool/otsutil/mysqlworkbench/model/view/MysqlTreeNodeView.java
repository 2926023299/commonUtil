package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MysqlTreeNodeView {

    private String key;

    private String label;

    private String type;

    private List<MysqlTreeNodeView> children = new ArrayList<MysqlTreeNodeView>();
}
