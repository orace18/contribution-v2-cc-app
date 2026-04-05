package com.ccapp.media.storage;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class UseLocalDevStorageCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return MediaStorageModeResolver.resolve(context.getEnvironment()).usesLocalDevStorage();
    }
}
