package com.tool.otsutil.model.dto.ApiDto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "areas")
public class AreaAlisa {
    private LinkedHashMap<String, String> area = new LinkedHashMap<>();

    private static Map<String, String> getDefaultArea() {
        Map<String, String> area = new HashMap<>();
        area.put("3096224743817217", "福州");
        area.put("3096224760594433", "厦门");
        area.put("3096224777371649", "泉州");
        area.put("3096224794148865", "漳州");
        area.put("3096224810926081", "龙岩");
        area.put("3096224827703297", "三明");
        area.put("3096224844480513", "宁德");
        area.put("3096224861257729", "南平");
        return area;
    }
}
