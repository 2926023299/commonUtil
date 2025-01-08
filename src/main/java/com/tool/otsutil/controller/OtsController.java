package com.tool.otsutil.controller;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.service.OtsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.text.ParseException;

@Slf4j
@RestController
@RequestMapping("/OtsUtil")
public class OtsController {
    // TODO 每天00点后主动执行
    // TODO 查询数据库中的ttu_measure 去判断量测值
    // TODO 获取数据库中的ttu_measure_type

    @Autowired
    private OtsService otsService;

    @PostMapping("save/{id}/{time}/{percentage}")
    public ResponseResult writeOneDayData(@PathVariable String id, @PathVariable String time, @PathVariable BigDecimal percentage) throws ParseException {
        log.info("save data to ots, id:{}, time:{}", id, time);

        if (id.isEmpty() || time.isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_REQUIRE);
        }

        // 判断time是否为20241111格式
        if (!time.matches("\\d{8}")) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, time);
        }

        otsService.saveOneDayData(id, time, percentage);

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "断点保存成功");
    }

    @PostMapping("setGlobalValue/{id}/{value}")
    public ResponseResult setGlobalValue(@PathVariable String id, @PathVariable BigDecimal value) {
        log.info("set global value to ots, id:{}, value:{}", id, value);

        try {
            otsService.setGlobalValue(id, value);
        } catch (Exception e) {
           return ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "设置全局值失败\n");
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "设置全局值成功\n");
    }

}
