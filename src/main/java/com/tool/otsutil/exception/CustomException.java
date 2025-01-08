package com.tool.otsutil.exception;

import com.tool.otsutil.model.common.AppHttpCodeEnum;
import lombok.Getter;

public class CustomException extends RuntimeException {

    @Getter
    private AppHttpCodeEnum appHttpCodeEnum;
    @Getter
    private String message;

    public CustomException(AppHttpCodeEnum appHttpCodeEnum){
        this.appHttpCodeEnum = appHttpCodeEnum;
    }

    public CustomException(AppHttpCodeEnum appHttpCodeEnum, String smg){
        this.appHttpCodeEnum = appHttpCodeEnum;
        this.message = smg;
    }
}