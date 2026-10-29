package com.example.baggage;

import java.util.concurrent.Callable;

/** OpenTelemetry's own context wrapping, the baseline for the OTel bridge. */
final class OtelOnly {

    private OtelOnly() {
    }

    static <T> Callable<T> wrap(Callable<T> task) {
        return io.opentelemetry.context.Context.current().wrap(task);
    }

}
