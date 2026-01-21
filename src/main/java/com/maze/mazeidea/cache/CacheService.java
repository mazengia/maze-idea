package com.maze.mazeidea.cache;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simple thread-safe bounded LRU cache for file contents.
 */
public class CacheService {
    private final int maxEntries;
    private final Map<Path, String> map;

    public CacheService(int maxEntries) {
        this.maxEntries = maxEntries;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, String> eldest) {
                return size() > CacheService.this.maxEntries;
            }
        };
    }

    public synchronized void put(Path path, String content) {
        map.put(path, content);
    }

    public synchronized Optional<String> get(Path path) {
        return Optional.ofNullable(map.get(path));
    }

    public synchronized void remove(Path path) {
        map.remove(path);
    }

    public synchronized void clear() {
        map.clear();
    }
}
