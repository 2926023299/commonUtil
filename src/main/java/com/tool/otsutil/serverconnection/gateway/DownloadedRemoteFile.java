package com.tool.otsutil.serverconnection.gateway;

import lombok.Data;

@Data
public class DownloadedRemoteFile {
    private String fileName;
    private String contentType;
    private byte[] content;
}
