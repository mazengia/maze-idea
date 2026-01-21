package com.maze.mazeidea.index;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryIndexStore implements IndexStore {
    private final Map<Path, String> store = new ConcurrentHashMap<>();

    @Override
    public void indexFile(Path path, String content) {
        store.put(path, content);
    }

    @Override
    public void remove(Path path) {
        store.remove(path);
    }

    @Override
    public List<Path> query(String q, int maxResults) {
        if (q == null || q.isEmpty()) return List.of();
        String lower = q.toLowerCase();
        return store.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().toLowerCase().contains(lower))
                .map(Map.Entry::getKey)
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    @Override
    public boolean contains(Path path) {
        return store.containsKey(path);
    }

    @Override
    public void close() {
        store.clear();
    }
}
