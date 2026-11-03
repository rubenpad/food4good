package com.food4good.shared.infrastructure.adapters.in.rest;

import java.util.Optional;

/** One type of live update. It remembers what changed, and turns that into a batch when asked. */
public interface LiveChannel {

    /** The SSE event name, e.g. {@code orders}. */
    String name();

    /** The current state of whatever changed since the last call, if anything did. */
    Optional<Object> drain();

    /** Forgets what changed, for when nobody is listening. A new client fetches everything anyway. */
    void discard();
}
