package com.tool.otsutil.model.dto.ApiDto;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private Object dataMap;
    private LinkedList<T> data;
    private int code;
}