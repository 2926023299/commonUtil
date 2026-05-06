package com.tool.otsutil.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * Keep authenticated sessions alive until explicit logout.
     */
    private boolean sessionNeverExpire = true;
}
