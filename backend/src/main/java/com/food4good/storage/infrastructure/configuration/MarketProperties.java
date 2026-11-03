package com.food4good.storage.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("food4good.market")
public record MarketProperties(String url, Duration connectTimeout, Duration readTimeout, int maxConcurrentCalls) {
}
