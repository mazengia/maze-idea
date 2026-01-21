package com.maze.mazeidea.index;

import com.maze.mazeidea.cache.CacheService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal indexer: delegates storage/search to an IndexStore implementation.
 */
public class Indexer {
    private final CacheService cache;
    private final IndexStore store;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> new Thread(r, "indexer-worker"));
    private final AtomicBoolean running = new AtomicBoolean(false);

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
        try { store.close(); } catch (Exception ignored) {}
    }

    public CompletableFuture<Void> indexFileAsync(Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (!Files.isRegularFile(path)) {
                    store.remove(path);
                    cache.remove(path);
                    return;
                }
                String content = Files.readString(path, StandardCharsets.UTF_8);
                store.indexFile(path, content);
                cache.put(path, content);
            } catch (IOException e) {
                store.remove(path);
                cache.remove(path);
            }
        }, executor);
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
}
