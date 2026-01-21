package com.maze.mazeidea.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class Debouncer {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "debouncer"));
    private final AtomicReference<ScheduledFuture<?>> last = new AtomicReference<>();

    public interface Handle {
        void cancel();
    }

    /**
     * Schedule a debounced task. Cancels the previous scheduled task on this Debouncer instance.
     * Returns a Handle that can cancel this specific scheduled task.
     */
    public Handle debounce(Runnable task, long delayMillis) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try { task.run(); } catch (Throwable t) { t.printStackTrace(); }
        }, delayMillis, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = last.getAndSet(future);
        if (previous != null) previous.cancel(false);
        return () -> future.cancel(false);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
