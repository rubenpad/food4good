package com.food4good.kitchen.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("food4good.kitchen")
record KitchenProperties(int cooks, double preparationTimeScale) {
}
