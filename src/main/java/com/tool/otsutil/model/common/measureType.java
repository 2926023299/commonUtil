package com.tool.otsutil.model.common;

import lombok.Getter;

import java.math.BigDecimal;

public enum measureType {
    ia(2096, 0.1),
    ib(2097, 0.1),
    ic(2098, 0.1);

    @Getter
    final int measure;

    final double percentage;

    measureType(int measure, double percentage) {
        this.measure = measure;
        this.percentage = percentage;
    }

    public BigDecimal getPercentage() {
        return BigDecimal.valueOf(percentage);
    }
}
