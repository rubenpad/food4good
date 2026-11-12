package com.food4good.shared.infrastructure.configuration;

import java.util.HashMap;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Sends traces, metrics and logs to Honeycomb over OTLP when {@code HONEYCOMB_API_KEY} is set, and
 * nothing anywhere when it isn't. Spring Boot only creates the trace and log exporters once they have
 * an endpoint, but its metrics exporter is on by default, so that one is switched off here.
 *
 * <p>These are defaults: any of them can still be overridden by ordinary configuration.
 */
public class HoneycombEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String ENABLED = "food4good.honeycomb.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var key = environment.getProperty("HONEYCOMB_API_KEY", "");
        var settings = new HashMap<String, Object>();
        if (key.isBlank()) {
            settings.put("management.otlp.metrics.export.enabled", false);
            settings.put(ENABLED, false);
        } else {
            var endpoint = environment.getProperty("HONEYCOMB_ENDPOINT", "https://api.honeycomb.io").replaceAll("/+$", "");
            settings.put(ENABLED, true);
            settings.put("management.opentelemetry.tracing.export.otlp.endpoint", endpoint + "/v1/traces");
            settings.put("management.opentelemetry.tracing.export.otlp.headers.x-honeycomb-team", key);
            settings.put("management.opentelemetry.logging.export.otlp.endpoint", endpoint + "/v1/logs");
            settings.put("management.opentelemetry.logging.export.otlp.headers.x-honeycomb-team", key);
            settings.put("management.otlp.metrics.export.enabled", true);
            settings.put("management.otlp.metrics.export.url", endpoint + "/v1/metrics");
            settings.put("management.otlp.metrics.export.headers.x-honeycomb-team", key);
            // Honeycomb keeps metrics in a dataset of their own; traces and logs go to the service's.
            settings.put("management.otlp.metrics.export.headers.x-honeycomb-dataset",
                    environment.getProperty("HONEYCOMB_METRICS_DATASET", "food4good-metrics"));
        }
        environment.getPropertySources().addLast(new MapPropertySource("honeycomb", settings));
    }
}
