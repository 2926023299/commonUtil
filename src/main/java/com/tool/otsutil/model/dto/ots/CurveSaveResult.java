package com.tool.otsutil.model.dto.ots;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CurveSaveResult {

    private final String type;
    private final String date;
    private final String templateDate;
    private final int writtenCount;
}
