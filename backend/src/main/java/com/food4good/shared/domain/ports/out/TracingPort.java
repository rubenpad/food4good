package com.food4good.shared.domain.ports.out;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Records the work done for Orders as spans, so one Order's journey can be followed end to end.
 *
 * <p>A span started inside another is its child. Otherwise it starts a new trace, which is how work
 * picked up from a queue begins: rather than keeping a trace open for as long as the work waited, it
 * links to the {@code cause}, the trace context stored with the work when it was queued.
 */
public interface TracingPort {

    /** Runs {@code work} in a span. If it throws, the span records the failure and the exception passes through. */
    <T> T call(String name, String cause, Map<String, ?> attributes, Supplier<T> work);

    default void run(String name, String cause, Map<String, ?> attributes, Runnable work) {
        call(name, cause, attributes, () -> {
            work.run();
            return null;
        });
    }

    /** Records something that has just happened, as a span with no duration to speak of. */
    default void record(String name, String cause, Map<String, ?> attributes) {
        run(name, cause, attributes, () -> {
        });
    }

    /** Adds an attribute to the current span, for a result known only once the work is done. */
    void tag(String key, Object value);

    /** Marks the current span as failed, for a failure that is handled rather than thrown. */
    void failed(Throwable failure);

    /** The current span's trace context, to store with work queued for later. Null outside a span. */
    String currentContext();
}
