package com.example.baggage;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.contextpropagation.ObservationAwareBaggageThreadLocalAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baggage created with the {@link Tracer} API, then a task handed over to another thread with
 * context-propagation inside an observation. The task runs after the baggage scope has closed.
 * Brave only accepts declared fields, so {@code tenant} is declared as a remote field.
 */
@SpringBootTest(properties = "management.tracing.baggage.remote-fields=tenant")
class BaggageAcrossThreadsTests {

    @Autowired
    Tracer tracer;

    @Autowired
    ObservationRegistry observationRegistry;

    final ExecutorService wrapped = ContextExecutorService.wrap(Executors.newSingleThreadExecutor());

    final ExecutorService plain = Executors.newSingleThreadExecutor();

    @AfterEach
    void cleanUp() {
        wrapped.shutdownNow();
        plain.shutdownNow();
        ContextRegistry.getInstance().removeThreadLocalAccessor(ObservationAwareBaggageThreadLocalAccessor.KEY);
    }

    @Test
    void baggageIsLostWithContextExecutorServiceByDefault() throws Exception {
        assertThat(baggageSeenAfterScopeCloses(wrapped::submit)).isNull();
    }

    @Test
    void baggageIsLostWithContextPropagatingTaskDecoratorByDefault() throws Exception {
        assertThat(baggageSeenAfterScopeCloses(this::viaTaskDecorator)).isNull();
    }

    @Test
    void baggageIsKeptWhenTheTaskRunsBeforeTheScopeCloses() throws Exception {
        assertThat(baggageSeen(wrapped::submit, false)).isEqualTo("acme");
    }

    @Test
    void baggageIsKeptWithObservationAwareBaggageThreadLocalAccessor() throws Exception {
        ContextRegistry.getInstance()
            .registerThreadLocalAccessor(new ObservationAwareBaggageThreadLocalAccessor(observationRegistry, tracer));

        assertThat(baggageSeenAfterScopeCloses(wrapped::submit)).isEqualTo("acme");
        assertThat(baggageSeenAfterScopeCloses(this::viaTaskDecorator)).isEqualTo("acme");
    }

    @Test
    @EnabledIfSystemProperty(named = "bridge", matches = "otel")
    void baggageIsKeptWithOpenTelemetryContextWrap() throws Exception {
        assertThat(baggageSeenAfterScopeCloses(task -> plain.submit(OtelOnly.wrap(task)))).isEqualTo("acme");
    }

    private String baggageSeenAfterScopeCloses(Function<Callable<String>, Future<String>> handOver) throws Exception {
        return baggageSeen(handOver, true);
    }

    /**
     * Each executor has a single thread. To make the task start (and its context get restored)
     * only after the scope has closed, a blocker occupies the thread until then, like a task that
     * is queued and runs after the request has finished.
     */
    private String baggageSeen(Function<Callable<String>, Future<String>> handOver, boolean afterScopeCloses)
            throws Exception {
        CountDownLatch scopeClosed = new CountDownLatch(1);
        Callable<String> task = () -> {
            Baggage baggage = tracer.getBaggage("tenant");
            return baggage == null ? null : baggage.get();
        };
        if (afterScopeCloses) {
            wrapped.submit(() -> scopeClosed.await(5, TimeUnit.SECONDS));
            plain.submit(() -> scopeClosed.await(5, TimeUnit.SECONDS));
        }
        Observation observation = Observation.start("work", observationRegistry);
        try (Observation.Scope ignored = observation.openScope()) {
            Future<String> result;
            try (BaggageInScope scope = tracer.createBaggageInScope("tenant", "acme")) {
                result = handOver.apply(task);
                if (!afterScopeCloses) {
                    return result.get(5, TimeUnit.SECONDS);
                }
            }
            scopeClosed.countDown();
            return result.get(5, TimeUnit.SECONDS);
        }
        finally {
            observation.stop();
        }
    }

    private Future<String> viaTaskDecorator(Callable<String> task) {
        FutureTask<String> future = new FutureTask<>(task);
        plain.execute(new ContextPropagatingTaskDecorator().decorate(future));
        return future;
    }

}
