package com.tool.otsutil.model.dto.inspection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ServerConfig {
    private String ip;
    private int port;
    private String username;
    private String password;
    private List<String> jars;
}