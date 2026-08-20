package com.jaungangton.api.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DevAuthProductionGuard {
    @Bean
    @Profile("!local & !dev")
    @ConditionalOnProperty(name = "centralton.dev.auth.enabled", havingValue = "true")
    Object rejectDevAuthOutsideDevelopment() {
        throw new IllegalStateException("DEV_AUTH_ENABLED=true is only allowed with local or dev profiles");
    }
}
