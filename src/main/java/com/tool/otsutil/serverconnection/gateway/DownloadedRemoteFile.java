package com.tool.otsutil.serverconnection.gateway;

import lombok.Data;

import java.io.InputStream;

@Data
public class DownloadedRemoteFile {
    private String fileName;
    private String contentType;
    private byte[] content;
    private InputStream contentStream;
    private long contentLength = -1;
}
