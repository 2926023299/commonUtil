package com.tool.otsutil.serverconnection.util;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PosixPathUtils {

    private PosixPathUtils() {
    }

    public static String normalize(String basePath, String requestedPath) {
        if (requestedPath != null && requestedPath.indexOf('\0') >= 0) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "非法路径");
        }

        String seed = (requestedPath == null || requestedPath.trim().isEmpty()) ? basePath : requestedPath.trim();
        String resolved;
        if (seed.startsWith("/")) {
            resolved = seed;
        } else {
            String safeBase = (basePath == null || basePath.trim().isEmpty()) ? "/" : basePath.trim();
            resolved = safeBase.endsWith("/") ? safeBase + seed : safeBase + "/" + seed;
        }

        Deque<String> parts = new ArrayDeque<String>();
        for (String part : resolved.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!parts.isEmpty()) {
                    parts.removeLast();
                }
                continue;
            }
            parts.addLast(part);
        }

        if (parts.isEmpty()) {
            return "/";
        }

        StringBuilder pathBuilder = new StringBuilder();
        for (String part : parts) {
            pathBuilder.append('/').append(part);
        }
        return pathBuilder.toString();
    }

    public static String parent(String path) {
        String normalized = normalize("/", path);
        if ("/".equals(normalized)) {
            return null;
        }

        int separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex <= 0) {
            return "/";
        }
        return normalized.substring(0, separatorIndex);
    }
}
