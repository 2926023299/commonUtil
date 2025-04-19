package com.tool.otsutil.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.model.dto.ApiDto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpClientUtil {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpClientUtil(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // 发送GET请求并解析返回的JSON数据
    public <T> ApiResponse<T> getForObject(String url, TypeReference<ApiResponse<T>> typeReference) {
        ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);
        String responseBody = responseEntity.getBody();

        try {
            return objectMapper.readValue(responseBody, typeReference);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize API response", e);
        }
    }
}

