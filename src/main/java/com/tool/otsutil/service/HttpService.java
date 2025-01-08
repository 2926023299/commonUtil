package com.tool.otsutil.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tool.otsutil.model.dto.ApiDto.ApiResponse;
import com.tool.otsutil.util.HttpClientUtil;
import org.springframework.stereotype.Service;

@Service
public class HttpService {
    private final HttpClientUtil httpClientUtil;

    /**
     * 构造函数
     * @param httpClientUtil http客户端工具
     */
    public HttpService(HttpClientUtil httpClientUtil) {
        this.httpClientUtil = httpClientUtil;
    }

    /**
     * 获取数据
     * @param url 请求地址
     * @param typeReference 泛型类型引用
     * @return ApiResponse 对象
     * @param <T> 泛型类型
     */
    public <T> ApiResponse<T> fetchData(String url, TypeReference<ApiResponse<T>> typeReference) {
        return httpClientUtil.getForObject(url, typeReference);
    }
}
