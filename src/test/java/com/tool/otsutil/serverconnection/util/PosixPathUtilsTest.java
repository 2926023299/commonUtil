package com.tool.otsutil.serverconnection.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PosixPathUtilsTest {

    @Test
    void shouldNormalizeRelativeSegments() {
        Assertions.assertEquals("/home/app/logs", PosixPathUtils.normalize("/home/app", "./logs"));
        Assertions.assertEquals("/home/data", PosixPathUtils.normalize("/home/app", "../data"));
        Assertions.assertEquals("/", PosixPathUtils.normalize("/home/app", "../../.."));
    }

    @Test
    void shouldReturnParentPath() {
        Assertions.assertEquals("/home/app", PosixPathUtils.parent("/home/app/logs"));
        Assertions.assertEquals("/", PosixPathUtils.parent("/logs"));
        Assertions.assertNull(PosixPathUtils.parent("/"));
    }
}
