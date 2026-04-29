package com.tool.otsutil.controller;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.exception.ExceptionCatch;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.ots.CurveSaveResult;
import com.tool.otsutil.model.dto.ots.CurveTemplateType;
import com.tool.otsutil.service.InspectionImpl.OtsService;
import com.tool.otsutil.service.brekerService.BreakerEnergyDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OtsControllerCurveSaveTest {

    private MockMvc mockMvc;

    private OtsService otsService;

    @BeforeEach
    void setUp() {
        otsService = mock(OtsService.class);

        OtsController otsController = new OtsController();
        ReflectionTestUtils.setField(otsController, "otsService", otsService);
        ReflectionTestUtils.setField(otsController, "breakerEnergyDataService", mock(BreakerEnergyDataService.class));

        mockMvc = MockMvcBuilders.standaloneSetup(otsController)
                .setControllerAdvice(new ExceptionCatch())
                .build();
    }

    @Test
    void shouldSaveCurveForSingleDay() throws Exception {
        given(otsService.saveCurveData("123456", CurveTemplateType.P, "20260423"))
                .willReturn(new CurveSaveResult("P", "20260423", "2026-04-22", 96));

        mockMvc.perform(post("/OtsUtil/saveCurve/123456/P/20260423"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.type").value("P"))
                .andExpect(jsonPath("$.data.date").value("20260423"))
                .andExpect(jsonPath("$.data.templateDate").value("2026-04-22"))
                .andExpect(jsonPath("$.data.writtenCount").value(96));
    }

    @Test
    void shouldRejectUnsupportedCurveType() throws Exception {
        mockMvc.perform(post("/OtsUtil/saveCurve/123456/X/20260423"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(501))
                .andExpect(jsonPath("$.otherMessage").value("type仅支持P或Q"));
    }

    @Test
    void shouldRejectInvalidCurveDate() throws Exception {
        mockMvc.perform(post("/OtsUtil/saveCurve/123456/P/2026-04-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(501))
                .andExpect(jsonPath("$.otherMessage").value("2026-04-23"));
    }

    @Test
    void shouldRejectBlankId() throws Exception {
        mockMvc.perform(post("/OtsUtil/saveCurve/%20/P/20260423"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void shouldReturnServerErrorWhenTemplateLoadFails() throws Exception {
        given(otsService.saveCurveData("123456", CurveTemplateType.Q, "20260423"))
                .willThrow(new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板文件不存在"));

        mockMvc.perform(post("/OtsUtil/saveCurve/123456/Q/20260423"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.otherMessage").value("曲线模板文件不存在"));
    }
}
