package com.maze.mazeidea;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // If Launcher did not initialize services, initialize them here using the startup arg or user.dir.
        try {
            java.util.List<String> params = getParameters().getRaw();
            Path projectRoot = (params != null && !params.isEmpty()) ? Paths.get(params.get(0)) : Paths.get(System.getProperty("user.dir"));
            if (WorkspaceManager.getWorkspaceRoot() == null) {
                WorkspaceManager.setWorkspace(projectRoot);
            }

            if (!ServiceManager.isInitialized()) {
                // initialize minimal services similar to Launcher
                com.maze.mazeidea.fs.FileWatcherService watcher = new com.maze.mazeidea.fs.FileWatcherService(projectRoot);
                com.maze.mazeidea.cache.CacheService cache = new com.maze.mazeidea.cache.CacheService(200);
                com.maze.mazeidea.index.Indexer indexer = new com.maze.mazeidea.index.Indexer(cache);
                com.maze.mazeidea.search.SearchService search = new com.maze.mazeidea.search.SearchService(indexer);
                com.maze.mazeidea.lsp.LspService lsp = new com.maze.mazeidea.lsp.LspService();

                ServiceManager.init(watcher, cache, indexer, search);
                ServiceManager.setLspService(lsp);

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

                watcher.start();
                indexer.start();

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try { watcher.stop(); } catch (Exception ignored) {}
                    try { indexer.stop(); } catch (Exception ignored) {}
                    try { cache.clear(); } catch (Exception ignored) {}
                    try { lsp.shutdown(); } catch (Exception ignored) {}
                }));
            }
        } catch (Exception ignored) {}

        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        scene.getStylesheets().add(HelloApplication.class.getResource("syntax-highlighting.css").toExternalForm());
        scene.getStylesheets().add(HelloApplication.class.getResource("ide-theme.css").toExternalForm());
        stage.setTitle("Maze IDE");
        stage.setScene(scene);
        stage.show();
    }
}
