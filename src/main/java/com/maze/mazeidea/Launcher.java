package com.maze.mazeidea;

import javafx.application.Application;

import com.maze.mazeidea.fs.FileWatcherService;
import com.maze.mazeidea.cache.CacheService;
import com.maze.mazeidea.index.Indexer;
import com.maze.mazeidea.search.SearchService;
import com.maze.mazeidea.lsp.LspService;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Launcher {
    public static void main(String[] args) {
        // Determine workspace root from CLI arg or user.dir
        Path projectRoot = (args != null && args.length > 0) ? Paths.get(args[0]) : Paths.get(System.getProperty("user.dir"));

        // set initial workspace so UI can pick it up
        WorkspaceManager.setWorkspace(projectRoot);

        FileWatcherService watcher = new FileWatcherService(projectRoot);
        CacheService cache = new CacheService(200);
        Indexer indexer = new Indexer(cache);
        SearchService search = new SearchService(indexer);
        LspService lsp = new LspService();

        // register services globally
        ServiceManager.init(watcher, cache, indexer, search);
        ServiceManager.setLspService(lsp);

        // wire up file events to the indexer
        watcher.registerListener(event -> {
            switch (event.type()) {
                case CREATE:
                case MODIFY:
                    indexer.indexFileAsync(event.path());
                    break;
                case DELETE:
                    indexer.remove(event.path());
                    break;
                default:
                    break;
            }
        });

        // start services
        watcher.start();
        indexer.start();

        // ensure background services are stopped on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                watcher.stop();
            } catch (Exception ignored) {}
            try {
                indexer.stop();
            } catch (Exception ignored) {}
            try {
                cache.clear();
            } catch (Exception ignored) {}
            try {
                lsp.shutdown();
            } catch (Exception ignored) {}
        }));

        Application.launch(HelloApplication.class, args);
    }
}
