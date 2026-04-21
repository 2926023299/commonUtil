package com.tool.otsutil.util;

import com.tool.otsutil.model.vo.inspection.JavaProcessDiffView;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class InspectionViewSupportTest {

    @Test
    void shouldSplitJavaProcessesAndRemoveBlankLines() {
        List<String> processes = InspectionViewSupport.splitJavaProcesses("alpha.jar\n\nbeta.jar\r\ngamma.jar\n");

        Assertions.assertEquals(Arrays.asList("alpha.jar", "beta.jar", "gamma.jar"), processes);
    }

    @Test
    void shouldDiffJavaProcesses() {
        JavaProcessDiffView diffView = InspectionViewSupport.diffJavaProcesses(
                Arrays.asList("alpha.jar", "beta.jar", "common.jar"),
                Arrays.asList("alpha.jar", "legacy.jar", "common.jar")
        );

        Assertions.assertEquals(Arrays.asList("beta.jar"), diffView.getAddedProcesses());
        Assertions.assertEquals(Arrays.asList("legacy.jar"), diffView.getRemovedProcesses());
        Assertions.assertEquals(Arrays.asList("alpha.jar", "common.jar"), diffView.getUnchangedProcesses());
    }
}
