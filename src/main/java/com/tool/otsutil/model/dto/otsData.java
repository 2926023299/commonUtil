package com.tool.otsutil.model.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class otsData {
    private String key;
    private String value;
    private String time;

    public otsData(String key) {
        this.key = key;
    }

    public otsData(String value, String key) {
        this.value = value;
        this.key = key;
    }

    public otsData(String key, String value, String time) {
        this.key = key;
        this.value = value;
        this.time = time;
    }
}
