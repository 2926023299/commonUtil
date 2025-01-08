package com.tool.otsutil.model.dto.inspection;

import com.tool.otsutil.config.InspectionConfig;
import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Getter
@Component
@Data
public class InspectionHeader {

    private static List<String> ipHeadersList = new ArrayList<>(Arrays.asList("25.87.14.218", "25.87.14.219", "25.87.14.220", "25.87.14.221"));

    private Map<String, LinkedList<String>> headersInfo = new HashMap<>();

    private LinkedList<String> headers = new LinkedList<>(Arrays.asList(
            InspectionCommon.DISK_USAGE_RATE,
            InspectionCommon.DISK_USAGE,
            InspectionCommon.MEMORY_USAGE,
            //InspectionCommon.MEMORY_USAGE_RATE,
            InspectionCommon.CPU_USAGE,
            InspectionCommon.THREAD_COUNT
            //InspectionCommon.JAVA_PROCESSES
    ));

    @Autowired
    private InspectionConfig inspectionConfig;

    //定义不同的ip有着不同的header
    public Map<String, LinkedList<String>> getHeaders() {

        for (ServerConfig server : inspectionConfig.getServers()) {

            if (server.getIp().equals("25.87.14.209")) {
                headersInfo.put(server.getIp(), new LinkedList<>(Arrays.asList(
                        InspectionCommon.DISK_USAGE_RATE,
                        InspectionCommon.DISK_USAGE,
                        InspectionCommon.SECOND_DISK_USAGE_RATE,
                        InspectionCommon.SECOND_DISK_USAGE,
                        InspectionCommon.THIRD_DISK_USAGE_RATE,
                        InspectionCommon.THIRD_SECOND_DISK_USAGE,
                        InspectionCommon.MEMORY_USAGE,
                        //InspectionCommon.MEMORY_USAGE_RATE,
                        InspectionCommon.CPU_USAGE,
                        InspectionCommon.THREAD_COUNT
                        //InspectionCommon.JAVA_PROCESSES
                )));
                continue;
            }

            if (ipHeadersList.contains(server.getIp())) {
                headersInfo.put(server.getIp(), new LinkedList<>(Arrays.asList(
                        InspectionCommon.DISK_USAGE_RATE,
                        InspectionCommon.DISK_USAGE,
                        InspectionCommon.SECOND_DISK_USAGE_RATE,
                        InspectionCommon.SECOND_DISK_USAGE,
                        InspectionCommon.MEMORY_USAGE,
                        //InspectionCommon.MEMORY_USAGE_RATE,
                        InspectionCommon.CPU_USAGE,
                        InspectionCommon.THREAD_COUNT
                        //InspectionCommon.JAVA_PROCESSES
                )));
                continue;
            }

            headersInfo.put(server.getIp(), headers);
        }

        System.out.println(headersInfo);
        return headersInfo;
    }
}
