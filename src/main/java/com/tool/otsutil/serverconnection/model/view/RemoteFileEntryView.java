package com.tool.otsutil.serverconnection.model.view;

import lombok.Data;

@Data
public class RemoteFileEntryView {
    private String name;
    private String path;
    private boolean directory;
    private long size;
    private long lastModified;
}
