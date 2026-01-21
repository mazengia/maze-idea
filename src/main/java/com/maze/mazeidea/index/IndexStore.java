package com.maze.mazeidea.index;

import java.nio.file.Path;
import java.util.List;

public interface IndexStore {
    void indexFile(Path path, String content);
    void remove(Path path);
    List<Path> query(String q, int maxResults);
    boolean contains(Path path);
    void close();
}
