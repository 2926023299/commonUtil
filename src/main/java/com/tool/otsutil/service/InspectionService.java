package com.tool.otsutil.service;

import cn.hutool.Hutool;
import cn.hutool.core.util.ObjectUtil;
import com.tool.otsutil.config.CommandsConfig;
import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.model.dto.inspection.InspectionCommon;
import com.tool.otsutil.model.dto.inspection.InspectionHeader;
import com.tool.otsutil.model.dto.inspection.InspectionResult;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.util.ExcelExportUtil;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class InspectionService {

    @Autowired
    private Environment environment; // 获取配置文件信息

    @Autowired
    private InspectionConfig inspectionConfig;

    @Autowired
    private ExcelExportUtil excelExportUtil;

    @Autowired
    InspectionHeader inspectionHeader;

    @Autowired
    private CommandsConfig commandsConfig;

    //进行各个服务器命令巡检
    public LinkedHashMap<String, InspectionResult> performInspection() {
        List<ServerConfig> servers = getServerConfigs();
        LinkedHashMap<String, InspectionResult> inspectionResultMap = new LinkedHashMap<>();

        InspectionResult inspectionResult;

        log.info("开始进行服务器巡检");

        for (ServerConfig server : servers) {
            try (SSHClient sshClient = connectToServer(server)) {
                //获取cpu使用率
                String cpuUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.CPU_USAGE)).replace("\n", "");
                //获取内存使用率
                String memoryTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.MEMORY_TOTAL)).replace("\n", "");
                //获取内存使用量
                String memoryUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.MEMORY_USAGE)).replace("\n", "");
                //获取内存使用率
                String memoryUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.MEMORY_USAGE_RATE)).replace("\n", "");
                //获取磁盘总量
                String diskTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.DISK_TOTAL)).replace("\n", "");
                //获取磁盘使用量
                String diskUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.DISK_USAGE)).replace("\n", "");
                //获取磁盘使用率
                String diskUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.DISK_USAGE_RATE)).replace("\n", "");
                // 获取线程数
                String threadCount = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.THREAD_COUNT)).replace("\n", "");
                // 获取java进程,并去掉最后一个换行符
                String jpsPath = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.JPS_PATH)).replace("\n", "");
                if(ObjectUtil.isEmpty(jpsPath) || jpsPath.isEmpty()){
                    jpsPath = "/usr/local/jdk1.8.0_121/bin/jps";
                }
                String javaProcesses = executeCommand(sshClient, jpsPath + commandsConfig.getCommand().get(InspectionCommon.JAVA_PROCESSES));
                if(ObjectUtil.isEmpty(javaProcesses)){
                    jpsPath = "/usr/local/jdk1.8.0_202/bin/jps";
                    javaProcesses = executeCommand(sshClient, jpsPath + commandsConfig.getCommand().get(InspectionCommon.JAVA_PROCESSES));
                }
                if(ObjectUtil.isEmpty(javaProcesses)){
                    jpsPath = "~/jdk1.8/bin/jps";
                    javaProcesses = executeCommand(sshClient, jpsPath + commandsConfig.getCommand().get(InspectionCommon.JAVA_PROCESSES));
                }

                // 去除最后一个换行符
                if (ObjectUtil.isNotEmpty(javaProcesses) && javaProcesses.endsWith("\n")) {
                    javaProcesses = javaProcesses.substring(0, javaProcesses.length() - 1);
                }

                String secondDiskUsage = "";
                String secondDiskTotal = "";
                String secondDiskUsageRate = "";
                if (!Objects.equals(server.getIp(), "25.87.14.209")) {
                    //获取第二块磁盘使用量
                    secondDiskUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_USAGE)).replace("\n", "");
                    // 获取第二块磁盘使用率
                    secondDiskUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_USAGE_RATE)).replace("\n", "");
                    //获取第二块磁盘总量
                    secondDiskTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_TOTAL)).replace("\n", "");

                }

                String thirdDiskUsage = "";
                String thirdDiskTotal = "";
                String thirdDiskUsageRate = "";
                if (Objects.equals(server.getIp(), "25.87.14.209")) {
                    //获取第二块磁盘使用量
                    secondDiskUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_USAGE_209)).replace("\n", "");
                    // 获取第二块磁盘使用率
                    secondDiskUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_USAGE_RATE_209)).replace("\n", "");
                    //获取第二块磁盘总量
                    secondDiskTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.SECOND_DISK_TOTAL_209)).replace("\n", "");

                    // 第三块硬盘
                    thirdDiskUsage = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.THIRD_SECOND_DISK_USAGE)).replace("\n", "");
                    thirdDiskUsageRate = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.THIRD_DISK_USAGE_RATE)).replace("\n", "");
                    thirdDiskTotal = executeCommand(sshClient, commandsConfig.getCommand().get(InspectionCommon.THIRD_SECOND_DISK_TOTAL)).replace("\n", "");

                }

                //进行结果的封装
                diskUsage = diskUsage + "/" + diskTotal;
                memoryUsage = memoryUsage + "/" + memoryTotal;
                secondDiskUsage = secondDiskUsage + "/" + secondDiskTotal;
                thirdDiskUsage = thirdDiskUsage + "/" + thirdDiskTotal;

                Map<String, String> result = new HashMap<>();
                result.put(InspectionCommon.IP, server.getIp());  // ip
                result.put(InspectionCommon.CPU_USAGE, cpuUsage); // cpu使用率
                result.put(InspectionCommon.MEMORY_USAGE_RATE, memoryUsageRate); // 内存使用率
                result.put(InspectionCommon.MEMORY_USAGE, memoryUsage); // 内存使用量
                result.put(InspectionCommon.DISK_USAGE_RATE, diskUsageRate); // 磁盘使用率
                result.put(InspectionCommon.DISK_USAGE, diskUsage); // 磁盘使用量
                result.put(InspectionCommon.THREAD_COUNT, threadCount); // 线程数
                result.put(InspectionCommon.JAVA_PROCESSES, javaProcesses); // java进程
                result.put(InspectionCommon.MEMORY_TOTAL, memoryTotal); // 内存总量
                result.put(InspectionCommon.DISK_TOTAL, diskTotal); // 磁盘总量
                result.put(InspectionCommon.SECOND_DISK_USAGE_RATE, secondDiskUsageRate); // 第二块磁盘使用率
                result.put(InspectionCommon.SECOND_DISK_USAGE, secondDiskUsage); // 第二块磁盘使用量
                result.put(InspectionCommon.SECOND_DISK_TOTAL, secondDiskTotal); // 第二块磁盘总量
                result.put(InspectionCommon.THIRD_DISK_USAGE_RATE, thirdDiskUsageRate);
                result.put(InspectionCommon.THIRD_SECOND_DISK_USAGE, thirdDiskUsage);
                result.put(InspectionCommon.THIRD_SECOND_DISK_TOTAL, thirdDiskTotal);

                log.info("获取服务器:{}信息:{}", server.getIp(), result);

                inspectionResult = new InspectionResult(result);

                inspectionResultMap.put(server.getIp(), inspectionResult);
            } catch (Exception e) {
                inspectionResultMap.put(server.getIp(), new InspectionResult());
                e.printStackTrace();
            }
        }

        return inspectionResultMap;
    }

    // 导入巡检日志excel到
    public void exportInspectionToExcel(String fileName) throws Exception {
        LinkedHashMap<String, InspectionResult> inspectionResultMap = performInspection();

        Map<String, LinkedList<String>> headers = inspectionHeader.getHeaders();

        // 获取模拟数据
        //LinkedHashMap<String, InspectionResult> inspectionResultMap = getMock();

        excelExportUtil.exportInspectionToExcel(fileName, headers, inspectionResultMap);
        excelExportUtil.exportJavaInspectionToExcel(fileName, inspectionResultMap);
        inspectionResultMap.forEach((key, value) -> {
            log.info("导出ip:{}", value.getResultInfo().get(InspectionCommon.IP));
            value.getResultInfo().forEach((key1, value1) -> {
                log.info("key:{} value:{}", key1, value1);
            });
        });
    }


    //获取模拟数据
    private static LinkedHashMap<String, InspectionResult> getMock() {
        LinkedHashMap<String, InspectionResult> stringInspectionResultMap = new LinkedHashMap<>();
        Map<String, String> result1 = new HashMap<>();
        result1.put(InspectionCommon.IP, "172.30.103.143");
        result1.put(InspectionCommon.CPU_USAGE, "80.3");
        result1.put(InspectionCommon.MEMORY_USAGE_RATE, "0.43");
        result1.put(InspectionCommon.MEMORY_USAGE, "12G/15G");
        result1.put(InspectionCommon.DISK_USAGE_RATE, "40%");
        result1.put(InspectionCommon.DISK_USAGE, "100G/500G");
        result1.put(InspectionCommon.THREAD_COUNT, "2999");
        result1.put(InspectionCommon.JAVA_PROCESSES, "7");
        Map<String, String> result2 = new HashMap<>();
        result2.put(InspectionCommon.IP, "172.30.103.144");
        result2.put(InspectionCommon.CPU_USAGE, "79");
        result2.put(InspectionCommon.MEMORY_USAGE_RATE, "0.83");
        result2.put(InspectionCommon.MEMORY_USAGE, "8G/15G");
        result2.put(InspectionCommon.DISK_USAGE_RATE, "83%");
        result2.put(InspectionCommon.DISK_USAGE, "400G/500G");
        result2.put(InspectionCommon.THREAD_COUNT, "4221");
        result2.put(InspectionCommon.JAVA_PROCESSES, "2");

        stringInspectionResultMap.put("172.30.103.143", new InspectionResult(result1));
        stringInspectionResultMap.put("172.30.103.144", new InspectionResult(result2));
        return stringInspectionResultMap;
    }

    //获取ssh连接
    public SSHClient connectToServer(ServerConfig server) throws IOException {
        SSHClient sshClient = null;
        try {
            sshClient = new SSHClient();
            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
            sshClient.connect(server.getIp(), server.getPort());
            sshClient.authPassword(server.getUsername(), server.getPassword());
        } catch (IOException e) {
            log.error(":服务器{}连接错误", server.getIp());
            throw new IOException(e);
        }
        return sshClient;
    }

    //执行命令
    public String executeCommand(SSHClient sshClient, String command) throws IOException {
        log.info(":执行命令:{}", command);
        Session session;

        session = sshClient.startSession();
        Session.Command cmd = session.exec(command);
        String result = IOUtils.readFully(cmd.getInputStream()).toString();
        cmd.join();
        session.close();
        return result;
    }

    //获取ssh的服务器配置
    public List<ServerConfig> getServerConfigs() {
        return inspectionConfig.getServers();
    }
}
