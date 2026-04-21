package com.tool.otsutil.serverconnection.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RemoteFileListView {
    private String cwd;
    private String parentPath;
    private List<RemoteBreadcrumbItemView> breadcrumbs = new ArrayList<RemoteBreadcrumbItemView>();
    private List<RemoteFileEntryView> entries = new ArrayList<RemoteFileEntryView>();
}
