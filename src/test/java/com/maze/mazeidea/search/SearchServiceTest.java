package com.maze.mazeidea.search;

import com.maze.mazeidea.cache.CacheService;
import com.maze.mazeidea.index.Indexer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class SearchServiceTest {
    @Test
    public void testSearchReturnsResults() throws Exception {
        Path tempFile = Files.createTempFile("sidx", ".txt");
        String content = "searchTerm appears here";
        Files.writeString(tempFile, content);

        CacheService cache = new CacheService(10);
        Indexer indexer = new Indexer(cache);
        indexer.start();
        indexer.indexFileAsync(tempFile).get();

        SearchService search = new SearchService(indexer);
        List<SearchService.SearchResult> results = search.search("searchTerm", 10);

        indexer.stop();

        assertFalse(results.isEmpty());
    }
}
