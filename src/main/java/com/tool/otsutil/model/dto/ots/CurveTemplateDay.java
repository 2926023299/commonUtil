package com.tool.otsutil.model.dto.ots;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class CurveTemplateDay {

    private final LocalDate sourceDate;
    private final List<BigDecimal> values;
    private final List<LocalTime> times;

    public CurveTemplateDay(LocalDate sourceDate, List<BigDecimal> values, List<LocalTime> times) {
        this.sourceDate = sourceDate;
        this.values = Collections.unmodifiableList(values);
        this.times = Collections.unmodifiableList(times);
    }

    public LocalDate getSourceDate() {
        return sourceDate;
    }

    public List<BigDecimal> getValues() {
        return values;
    }

    public List<LocalTime> getTimes() {
        return times;
    }
}
