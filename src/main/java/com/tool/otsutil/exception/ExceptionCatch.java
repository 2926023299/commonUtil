package com.tool.otsutil.exception;

import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.common.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ExceptionCatch {

    @ExceptionHandler(Exception.class)
    public ResponseResult<?> exception(Exception e) {
        log.error("catch exception", e);
        return ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseResult<?> exception(CustomException e) {
        log.error("catch custom exception", e);
        return ResponseResult.errorResult(e.getAppHttpCodeEnum(), e.getMessage());
    }

    @ExceptionHandler(IgnoreGlobalExceptionHandler.class)
    public void exception(IgnoreGlobalExceptionHandler e) {
        log.warn("ignore global exception: {}", e.getMessage());
    }
}
