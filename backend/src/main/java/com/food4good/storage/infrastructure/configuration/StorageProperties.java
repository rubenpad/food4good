package com.food4good.storage.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("food4good.storage")
public record StorageProperties(Duration reconcileInterval) {
}
