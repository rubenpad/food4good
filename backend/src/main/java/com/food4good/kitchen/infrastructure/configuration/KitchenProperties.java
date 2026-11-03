package com.food4good.kitchen.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("food4good.kitchen")
record KitchenProperties(int cooks, Duration maxWait, double preparationTimeScale) {
}
