package com.tool.otsutil.model.common;

import org.springframework.stereotype.Component;

@Component
public class GlobalOTSValue {
    //全局ots的默认值的后缀
    final public static String GlobalOTS_SUFFIX = "99999999999999";

    public String getKey(String key) {
        return key + GlobalOTS_SUFFIX;
    }

}
