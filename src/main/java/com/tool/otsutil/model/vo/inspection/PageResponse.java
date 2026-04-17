package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> records = new ArrayList<T>();
    private long total;
    private long page;
    private long pageSize;
}
