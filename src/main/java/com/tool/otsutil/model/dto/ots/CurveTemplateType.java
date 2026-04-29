package com.tool.otsutil.model.dto.ots;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;

public enum CurveTemplateType {
    P("有功P"),
    Q("无功Q");

    private final String templateName;

    CurveTemplateType(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateName() {
        return templateName;
    }

    public static CurveTemplateType fromCode(String code) {
        if (code == null) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "type不能为空");
        }

        for (CurveTemplateType value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }

        throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "type仅支持P或Q");
    }
}
