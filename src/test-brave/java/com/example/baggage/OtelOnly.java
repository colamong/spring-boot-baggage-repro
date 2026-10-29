package com.example.baggage;

import java.util.concurrent.Callable;

/** No OpenTelemetry context with the Brave bridge; the baseline is not run. */
final class OtelOnly {

    private OtelOnly() {
    }

    static <T> Callable<T> wrap(Callable<T> task) {
        throw new UnsupportedOperationException("OpenTelemetry baseline only runs with the OTel bridge");
    }

}
