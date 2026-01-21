package com.maze.mazeidea.search;

import com.maze.mazeidea.index.Indexer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SearchService {
    private final Indexer indexer;

    public SearchService(Indexer indexer) {
        this.indexer = indexer;
    }

    public List<SearchResult> search(String query, int maxResults) {
        List<Path> paths = indexer.queryBySubstring(query);
        return paths.stream().limit(maxResults).map(p -> {
            String content = "";
            try { content = indexer.contains(p) ? indexer.queryBySubstring("").toString() : ""; } catch (Exception ignored) {}
            return new SearchResult(p, -1, "(match)");
        }).collect(Collectors.toList());
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
