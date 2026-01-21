package com.maze.mazeidea.index;

import com.maze.mazeidea.cache.CacheService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IndexerTest {
    @Test
    public void testIndexAndQuery() throws Exception {
        Path tempFile = Files.createTempFile("idx", ".txt");
        String content = "this is a uniqueToken for testing";
        Files.writeString(tempFile, content);

        CacheService cache = new CacheService(10);
        Indexer indexer = new Indexer(cache);
        indexer.start();

        indexer.indexFileAsync(tempFile).get();

        List<java.nio.file.Path> results = indexer.queryBySubstring("uniqueToken");

        indexer.stop();

        assertTrue(results.contains(tempFile));
    }
}
