package com.maze.mazeidea.index;

import com.maze.mazeidea.cache.CacheService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal indexer: delegates storage/search to an IndexStore implementation.
 */
public class Indexer {
    private final CacheService cache;
    private final IndexStore store;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> new Thread(r, "indexer-worker"));
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "indexer-batcher"));
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Path> pending = new ConcurrentLinkedQueue<>();
    private final Object batchLock = new Object();
    private CompletableFuture<Void> scheduledBatch;
    private final ConcurrentHashMap<Path, FileStamp> stamps = new ConcurrentHashMap<>();

    private static final int BATCH_MAX = 200;
    private static final long BATCH_DELAY_MS = 150;

    public Indexer(CacheService cache) {
        this(cache, new InMemoryIndexStore());
    }

    public Indexer(CacheService cache, IndexStore store) {
        this.cache = cache;
        this.store = store;
    }

    public void start() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
        executor.shutdownNow();
        scheduler.shutdownNow();
        try { store.close(); } catch (Exception ignored) {}
    }

    public CompletableFuture<Void> indexFileAsync(Path path) {
        if (!running.get()) return CompletableFuture.completedFuture(null);
        pending.add(path);
        return scheduleBatch();
    }

    public void remove(Path path) {
        store.remove(path);
        cache.remove(path);
    }

    public List<Path> queryBySubstring(String q) {
        if (q == null || q.isEmpty()) return List.of();
        return store.query(q, Integer.MAX_VALUE);
    }

    // For tests: allow seeding
    public void seed(Path path, String content) {
        store.indexFile(path, content);
        cache.put(path, content);
    }

    public boolean contains(Path path) {
        return cache.get(path).isPresent() || store.contains(path);
    }

    public java.util.Optional<String> getCachedContent(Path path) {
        return cache.get(path);
    }

    private CompletableFuture<Void> scheduleBatch() {
        synchronized (batchLock) {
            if (scheduledBatch == null || scheduledBatch.isDone()) {
                scheduledBatch = new CompletableFuture<>();
                scheduler.schedule(() -> flushBatch(scheduledBatch), BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
            }
            return scheduledBatch;
        }
    }

    private void flushBatch(CompletableFuture<Void> batchFuture) {
        Set<Path> dedup = new LinkedHashSet<>();
        while (dedup.size() < BATCH_MAX) {
            Path p = pending.poll();
            if (p == null) break;
            dedup.add(p);
        }
        if (dedup.isEmpty()) {
            batchFuture.complete(null);
            return;
        }
        CompletableFuture.runAsync(() -> {
            for (Path p : dedup) {
                indexFileNow(p);
            }
        }, executor).whenComplete((r, ex) -> {
            if (ex != null) batchFuture.completeExceptionally(ex);
            else batchFuture.complete(null);
            if (!pending.isEmpty()) scheduleBatch();
        });
    }

    private void indexFileNow(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                store.remove(path);
                cache.remove(path);
                stamps.remove(path);
                return;
            }
            long size = Files.size(path);
            long modified = Files.getLastModifiedTime(path).toMillis();
            FileStamp previous = stamps.get(path);
            if (previous != null && previous.size == size && previous.modified == modified && cache.get(path).isPresent()) {
                return;
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            store.indexFile(path, content);
            cache.put(path, content);
            stamps.put(path, new FileStamp(size, modified));
        } catch (IOException e) {
            store.remove(path);
            cache.remove(path);
            stamps.remove(path);
        }
    }

    private static final class FileStamp {
        private final long size;
        private final long modified;

        private FileStamp(long size, long modified) {
            this.size = size;
            this.modified = modified;
        }
    }
}
