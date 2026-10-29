# Baggage across context-propagation thread hops in Spring Boot

Baggage created with the `Tracer` API inside an observation, then a task handed to another
thread with context-propagation (`ContextExecutorService`, `ContextPropagatingTaskDecorator`).
When the task starts after the baggage scope has closed, the baggage is gone on the other
thread. The trace carries over.

Micrometer Tracing documents that `ObservationAwareBaggageThreadLocalAccessor` "is required to
propagate baggage created by the user". Spring Boot doesn't register it. With it registered, the
baggage is kept.

## Run

```
./gradlew test                  # OpenTelemetry bridge
./gradlew test -Pbridge=brave   # Brave bridge
```

The tests pin the current behavior, so they pass:

| test | result |
|---|---|
| `baggageIsLostWithContextExecutorServiceByDefault` | baggage is `null` |
| `baggageIsLostWithContextPropagatingTaskDecoratorByDefault` | baggage is `null` |
| `baggageIsKeptWhenTheTaskRunsBeforeTheScopeCloses` | `acme` |
| `baggageIsKeptWithObservationAwareBaggageThreadLocalAccessor` | `acme` |
| `baggageIsKeptWithOpenTelemetryContextWrap` (OTel bridge only) | `acme` |

Checked with Spring Boot 4.1.1, 4.1.2 and 4.2.0-RC1 (Micrometer Tracing 1.7.1, 1.7.2 and
1.8.0-RC1) on both bridges. Brave only accepts declared baggage fields, so the tests set
`management.tracing.baggage.remote-fields=tenant`.

To make the task start only after the scope has closed, a blocker occupies the single executor
thread until then, like a task that is queued and runs after the request has finished.

## Related

- micrometer-metrics/tracing#879, #504, #561: the same problem, each solved by registering the
  accessors by hand
