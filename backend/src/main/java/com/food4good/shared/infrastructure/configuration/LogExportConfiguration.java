package com.food4good.shared.infrastructure.configuration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sends every log line to the OpenTelemetry SDK, which exports it with the trace it was logged in.
 * Spring Boot exports logs over OTLP but leaves bridging Logback to the application.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(HoneycombEnvironmentPostProcessor.ENABLED)
class LogExportConfiguration {

    @Bean
    OpenTelemetryAppender openTelemetryAppender(OpenTelemetry openTelemetry) {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var appender = new OpenTelemetryAppender();
        appender.setContext(context);
        appender.setName("OTEL");
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        OpenTelemetryAppender.install(openTelemetry);
        return appender;
    }
}
