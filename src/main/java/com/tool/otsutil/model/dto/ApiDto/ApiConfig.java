package com.tool.otsutil.model.dto.ApiDto;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.Getter;

@Getter
@Data
public class ApiConfig {
    private String urlTemplate;
    private TypeReference<?> responseType;

    public ApiConfig(String urlTemplate, TypeReference<?> responseType) {
        this.urlTemplate = urlTemplate;
        this.responseType = responseType;
    }

}