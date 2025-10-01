package com.tool.otsutil.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.model.dto.ApiDto.ApiResponse;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class HttpClientUtil {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpClientUtil(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${getTokenUser}")
    private String username;

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

    // 获取token的POST接口
    public ResponseEntity<TokenResponse> getToken(String url, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String jsonBody = "{\"userName\":\"" + username + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        log.info("请求获取token的URL: {}, 请求体: {}", url, jsonBody);
        ResponseEntity<TokenResponse> responseEntity = restTemplate.postForEntity(url, entity, TokenResponse.class);

        return responseEntity;
    }

    // 修改原有请求方法，支持accessToken放在请求头
    public <T> ApiResponse<T> getForObjectWithToken(String url, TypeReference<ApiResponse<T>> typeReference) {

        ResponseEntity<TokenResponse> token = getToken("http://25.86.161.156:20045/system/api/authorization", username);
        TokenResponse tokenResponse = token.getBody();

        log.info("获取token成功：{}", tokenResponse);

        HttpHeaders headers = new HttpHeaders();
		if (tokenResponse != null) {
			headers.set("accessToken", tokenResponse.getAccessToken());
		}

		HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> responseEntity = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
        String responseBody = responseEntity.getBody();

        try {
            return objectMapper.readValue(responseBody, typeReference);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize API response", e);
        }
    }

}

// TokenResponse类
@ToString
class TokenResponse {
    private String code;
    private String message;
    private String accessToken;

    // getter和setter
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
