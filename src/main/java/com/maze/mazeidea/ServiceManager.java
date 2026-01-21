package com.maze.mazeidea;

import com.maze.mazeidea.cache.CacheService;
import com.maze.mazeidea.fs.FileWatcherService;
import com.maze.mazeidea.index.Indexer;
import com.maze.mazeidea.search.SearchService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ServiceManager {
    private static volatile FileWatcherService fileWatcher;
    private static volatile CacheService cacheService;
    private static volatile Indexer indexer;
    private static volatile SearchService searchService;
    private static volatile Object lspService; // keep generic to avoid tight coupling initially

    private ServiceManager() {}

    public static void init(FileWatcherService watcher, CacheService cache, Indexer idx, SearchService search) {
        fileWatcher = watcher;
        cacheService = cache;
        indexer = idx;
        searchService = search;
    }

    public static FileWatcherService getFileWatcher() { return fileWatcher; }
    public static CacheService getCacheService() { return cacheService; }
    public static Indexer getIndexer() { return indexer; }
    public static SearchService getSearchService() { return searchService; }

    public static boolean isInitialized() { return indexer != null; }

    // LSP service accessor - object typed to avoid requiring the class before it's created
    public static void setLspService(Object svc) { lspService = svc; }
    public static <T> T getLspService(Class<T> cls) { return cls.cast(lspService); }

    /**
     * Switch the watched workspace root: stop the current watcher, create a new watcher for the new root,
     * re-wire events to the existing indexer, start watching, and perform an initial indexing of files.
     */
    public static synchronized void switchWorkspace(Path newRoot) {
        try {
            if (fileWatcher != null) {
                try { fileWatcher.stop(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // stop LSP for previous workspace
        try {
            com.maze.mazeidea.lsp.LspService lsp = getLspService(com.maze.mazeidea.lsp.LspService.class);
            if (lsp != null) {
                Path prev = (fileWatcher != null) ? fileWatcher.getRoot() : null;
                if (prev != null) lsp.stopForWorkspace(prev);
            }
        } catch (Exception ignored) {}

        try {
            FileWatcherService watcher = new FileWatcherService(newRoot);
            // wire up file events to the indexer
            watcher.registerListener(event -> {
                switch (event.type()) {
                    case CREATE:
                    case MODIFY:
                        if (indexer != null) indexer.indexFileAsync(event.path());
                        break;
                    case DELETE:
                        if (indexer != null) indexer.remove(event.path());
                        break;
                    default:
                        break;
                }
            });
            watcher.start();
            fileWatcher = watcher;

            // start LSP for new workspace
            try {
                com.maze.mazeidea.lsp.LspService lsp = getLspService(com.maze.mazeidea.lsp.LspService.class);
                if (lsp != null) lsp.startForWorkspace(newRoot);
            } catch (Exception ignored) {}

            // re-index existing files under new root
            if (indexer != null) {
                List<Path> toIndex = new ArrayList<>();
                try {
                    Files.walk(newRoot)
                            .filter(Files::isRegularFile)
                            .limit(10000)
                            .forEach(toIndex::add);
                } catch (Exception ignored) {}
                for (Path p : toIndex) {
                    try { indexer.indexFileAsync(p); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            // if watcher creation failed, leave previous state
            System.err.println("Failed to switch workspace watcher: " + e.getMessage());
        }

    }
}
