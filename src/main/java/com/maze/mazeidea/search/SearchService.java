package com.maze.mazeidea.search;

import com.maze.mazeidea.index.Indexer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SearchService {
    private final Indexer indexer;

    public SearchService(Indexer indexer) {
        this.indexer = indexer;
    }

    public List<SearchResult> search(String query, int maxResults) {
        List<Path> paths = indexer.queryBySubstring(query);
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;
        String needle = query.toLowerCase();
        for (Path p : paths) {
            if (results.size() >= maxResults) break;
            SearchResult r = buildResult(p, needle);
            results.add(r);
        }
        return results;
    }

    private SearchResult buildResult(Path path, String needle) {
        String content = readContent(path).orElse("");
        if (!content.isEmpty()) {
            String[] lines = content.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.toLowerCase().contains(needle)) {
                    return new SearchResult(path, i + 1, line.trim());
                }
            }
        }
        return new SearchResult(path, -1, "(match)");
    }

    private Optional<String> readContent(Path path) {
        try {
            Optional<String> cached = indexer.getCachedContent(path);
            if (cached.isPresent()) return cached;
        } catch (Exception ignored) {}
        try {
            if (Files.isRegularFile(path)) return Optional.of(Files.readString(path));
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public static class SearchResult {
        private final Path path;
        private final int line;
        private final String snippet;

        public SearchResult(Path path, int line, String snippet) {
            this.path = path;
            this.line = line;
            this.snippet = snippet;
        }

        public Path getPath() { return path; }
        public int getLine() { return line; }
        public String getSnippet() { return snippet; }
    }
}
