package com.jaungangton.api.auth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class DevMockGoogleAuthEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("centralton.dev.auth.enabled", "false");
        String headerValue = context.getEnvironment().getProperty("centralton.dev.auth.header-value", "");
        return "true".equalsIgnoreCase(enabled) && !headerValue.isBlank();
    }
}
