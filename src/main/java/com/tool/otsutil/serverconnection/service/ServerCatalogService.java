package com.tool.otsutil.serverconnection.service;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.config.InspectionConfig;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.serverconnection.model.view.ServerConnectionView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ServerCatalogService {

    private final InspectionConfig inspectionConfig;

    public ServerCatalogService(InspectionConfig inspectionConfig) {
        this.inspectionConfig = inspectionConfig;
    }

    public List<ServerConnectionView> listServers() {
        Map<String, ServerConnectionView> uniqueServers = new LinkedHashMap<String, ServerConnectionView>();
        if (inspectionConfig.getServers() == null) {
            return new ArrayList<ServerConnectionView>();
        }

        for (ServerConfig serverConfig : inspectionConfig.getServers()) {
            String serverKey = buildServerKey(serverConfig);
            ServerConnectionView current = uniqueServers.get(serverKey);
            if (current == null) {
                current = new ServerConnectionView();
                current.setServerKey(serverKey);
                current.setDisplayName(buildDisplayName(serverConfig));
                current.setIp(serverConfig.getIp());
                current.setPort(serverConfig.getPort());
                current.setUsername(serverConfig.getUsername());
                current.setJars(new ArrayList<String>());
                uniqueServers.put(serverKey, current);
            }

            if (serverConfig.getJars() != null) {
                LinkedHashSet<String> deduped = new LinkedHashSet<String>(current.getJars());
                for (String jar : serverConfig.getJars()) {
                    String jarName = jar == null ? "" : jar.split(":")[0];
                    if (!jarName.isEmpty() && !"null".equalsIgnoreCase(jarName)) {
                        deduped.add(jarName);
                    }
                }
                current.setJars(new ArrayList<String>(deduped));
            }
        }

        return new ArrayList<ServerConnectionView>(uniqueServers.values());
    }

    public ServerConfig getServerConfig(String serverKey) {
        if (inspectionConfig.getServers() == null) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "服务器配置不存在");
        }

        for (ServerConfig serverConfig : inspectionConfig.getServers()) {
            if (buildServerKey(serverConfig).equals(serverKey)) {
                return serverConfig;
            }
        }
        throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "服务器配置不存在");
    }

    public static String buildServerKey(ServerConfig serverConfig) {
        return serverConfig.getIp() + ":" + serverConfig.getPort() + ":" + serverConfig.getUsername();
    }

    public String buildDisplayName(ServerConfig serverConfig) {
        return serverConfig.getIp() + ":" + serverConfig.getPort() + " (" + serverConfig.getUsername() + ")";
    }
}
