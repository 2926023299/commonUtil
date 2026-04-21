package com.tool.otsutil.exception;

import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.common.ResponseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExceptionCatchTest {

    private final ExceptionCatch exceptionCatch = new ExceptionCatch();

    @Test
    void genericExceptionShouldReturnStandardServerError() {
        ResponseResult<?> result = exceptionCatch.exception(new RuntimeException("internal detail"));

        assertEquals(AppHttpCodeEnum.SERVER_ERROR.getCode(), result.getCode());
        assertEquals(AppHttpCodeEnum.SERVER_ERROR.getMessage(), result.getErrorMessage());
        assertNull(result.getOtherMessage());
    }

    @Test
    void customExceptionShouldPreserveBusinessMessage() {
        ResponseResult<?> result = exceptionCatch.exception(new CustomException(AppHttpCodeEnum.PARAM_INVALID, "参数错误"));

        assertEquals(AppHttpCodeEnum.PARAM_INVALID.getCode(), result.getCode());
        assertEquals(AppHttpCodeEnum.PARAM_INVALID.getMessage(), result.getErrorMessage());
        assertEquals("参数错误", result.getOtherMessage());
    }
}
