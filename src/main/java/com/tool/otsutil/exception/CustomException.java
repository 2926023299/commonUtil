package com.tool.otsutil.exception;

import com.tool.otsutil.model.common.AppHttpCodeEnum;
import lombok.Getter;

public class CustomException extends RuntimeException {

    @Getter
    private final AppHttpCodeEnum appHttpCodeEnum;

    public CustomException(AppHttpCodeEnum appHttpCodeEnum) {
        super(appHttpCodeEnum.getMessage());
        this.appHttpCodeEnum = appHttpCodeEnum;
    }

    public CustomException(AppHttpCodeEnum appHttpCodeEnum, String message) {
        super(message);
        this.appHttpCodeEnum = appHttpCodeEnum;
    }
}
