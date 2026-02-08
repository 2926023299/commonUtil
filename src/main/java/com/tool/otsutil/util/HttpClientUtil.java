package com.tool.otsutil.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.model.dto.ApiDto.ApiResponse;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
    
    @Value("${token.url:http://25.86.161.156:20045/system/api/authorization}")
    private String tokenUrl;
    
    // Token缓存相关字段
    private TokenResponse cachedToken;
    private LocalDateTime tokenExpireTime;
    private static final long TOKEN_EXPIRE_MINUTES = 30; // Token有效期30分钟

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
    
    // 获取token请求的请求体
    @Data
    private static class TokenRequest {
        private String userName;
        
        public TokenRequest(String userName) {
            this.userName = userName;
        }
    }

    // 获取token的POST接口，带缓存机制
    public TokenResponse getToken() {
        // 检查缓存的token是否有效
        if (cachedToken != null && LocalDateTime.now().isBefore(tokenExpireTime)) {
            log.info("使用缓存的token");
            return cachedToken;
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            // 使用对象构建JSON，避免字符串拼接
            TokenRequest tokenRequest = new TokenRequest(username);
            String jsonBody = objectMapper.writeValueAsString(tokenRequest);
            
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            log.info("请求获取token的URL: {}, 请求体: {}", tokenUrl, jsonBody);
            
            ResponseEntity<TokenResponse> responseEntity = restTemplate.postForEntity(tokenUrl, entity, TokenResponse.class);
            TokenResponse tokenResponse = responseEntity.getBody();
            
            if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                // 缓存token并设置过期时间
                cachedToken = tokenResponse;
                tokenExpireTime = LocalDateTime.now().plus(TOKEN_EXPIRE_MINUTES, ChronoUnit.MINUTES);
                log.info("获取token成功，有效期至: {}", tokenExpireTime);
            }
            
            return tokenResponse;
        } catch (Exception e) {
            log.error("获取token失败", e);
            throw new RuntimeException("Failed to get token", e);
        }
    }

    // 修改原有请求方法，accessToken放在请求头
    public <T> ApiResponse<T> getForObjectWithToken(String url, TypeReference<ApiResponse<T>> typeReference) {
        // 获取token（会自动使用缓存）
        TokenResponse tokenResponse = getToken();

        log.info("使用token: {}", tokenResponse);

        HttpHeaders headers = new HttpHeaders();
		if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
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
