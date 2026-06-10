package com.tool.otsutil.serverconnection.model.request;

import lombok.Data;

@Data
public class OpenTerminalSessionRequest {
    private String serverKey;
    private String initialPath;

    /**
     * 服务器字符编码，默认为 UTF-8。
     * 对于使用 GBK 编码的 Windows 服务器，应设置为 "GBK"。
     */
    private String charset = "UTF-8";
}
