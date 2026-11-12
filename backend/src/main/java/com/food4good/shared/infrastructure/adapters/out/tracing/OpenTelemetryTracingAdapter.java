package com.food4good.shared.infrastructure.adapters.out.tracing;

import com.food4good.shared.domain.ports.out.TracingPort;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Spans through the OpenTelemetry SDK that Spring Boot configures, so they share a trace with the
 * HTTP and market-call spans Spring records. Trace contexts are stored as W3C {@code traceparent}.
 */
@Component
class OpenTelemetryTracingAdapter implements TracingPort {

    private final Tracer tracer;

    OpenTelemetryTracingAdapter(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("food4good");
    }

    @Override
    public <T> T call(String name, String cause, Map<String, ?> attributes, Supplier<T> work) {
        var builder = tracer.spanBuilder(name).setAllAttributes(toAttributes(attributes));
        var link = parse(cause);
        if (link.isValid()) {
            builder.addLink(link);
        }
        var span = builder.startSpan();
        try (var ignored = span.makeCurrent()) {
            return work.get();
        } catch (RuntimeException | Error e) {
            fail(span, e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Override
    public void tag(String key, Object value) {
        Span.current().setAllAttributes(toAttributes(Map.of(key, value)));
    }

    @Override
    public void failed(Throwable failure) {
        fail(Span.current(), failure);
    }

    @Override
    public String currentContext() {
        var context = Span.current().getSpanContext();
        if (!context.isValid()) {
            return null;
        }
        return "00-%s-%s-%s".formatted(context.getTraceId(), context.getSpanId(), context.getTraceFlags().asHex());
    }

    private static void fail(Span span, Throwable failure) {
        span.recordException(failure);
        span.setStatus(StatusCode.ERROR, failure.toString());
    }

    /** Reads what {@link #currentContext()} writes. Anything else, including null, is no link. */
    private static SpanContext parse(String traceparent) {
        if (traceparent == null) {
            return SpanContext.getInvalid();
        }
        var parts = traceparent.split("-");
        if (parts.length != 4 || parts[3].length() != 2) {
            return SpanContext.getInvalid();
        }
        return SpanContext.createFromRemoteParent(
                parts[1], parts[2], TraceFlags.fromHex(parts[3], 0), TraceState.getDefault());
    }

    /** Whole numbers stay numbers, so Honeycomb can sum and compare them. */
    private static Attributes toAttributes(Map<String, ?> values) {
        var attributes = Attributes.builder();
        values.forEach((key, value) -> {
            switch (value) {
                case Integer number -> attributes.put(AttributeKey.longKey(key), number.longValue());
                case Long number -> attributes.put(AttributeKey.longKey(key), number);
                case Boolean flag -> attributes.put(AttributeKey.booleanKey(key), flag);
                case Double number -> attributes.put(AttributeKey.doubleKey(key), number);
                default -> attributes.put(AttributeKey.stringKey(key), String.valueOf(value));
            }
        });
        return attributes.build();
    }
}
