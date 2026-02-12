package com.maze.mazeidea;

import com.maze.mazeidea.cache.CacheService;
import com.maze.mazeidea.index.Indexer;
import com.maze.mazeidea.search.SearchService;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchWindowController {
    @FXML
    public TextField queryField;
    @FXML
    public Button searchButton;
    @FXML
    public ListView<String> resultsList;

    private final SearchService searchService;
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "search-worker"));

    public SearchWindowController() {
        // prefer global search service if initialized
        if (ServiceManager.isInitialized() && ServiceManager.getSearchService() != null) {
            this.searchService = ServiceManager.getSearchService();
        } else {
            // fallback local search service for demo/testing
            CacheService cache = new CacheService(200);
            Indexer indexer = new Indexer(cache);
            indexer.start();
            this.searchService = new SearchService(indexer);
        }
    }

    @FXML
    public void initialize() {
        searchButton.setOnAction(e -> doSearch());
        queryField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((wObs, oldW, newW) -> {
                    if (newW != null) newW.setOnHidden(e -> shutdownExecutor());
                });
            }
        });
    }

    private void doSearch() {
        String q = queryField.getText();
        if (q == null || q.isEmpty()) return;
        searchButton.setDisable(true);
        searchExecutor.submit(() -> {
            List<SearchService.SearchResult> results = searchService.search(q, 50);
            Platform.runLater(() -> {
                resultsList.getItems().clear();
                for (SearchService.SearchResult r : results) {
                    Path p = r.getPath();
                    resultsList.getItems().add(p.toString() + " : " + r.getSnippet());
                }
                searchButton.setDisable(false);
            });
        });
    }

    public void close() {
        shutdownExecutor();
        try { ((Stage) queryField.getScene().getWindow()).close(); } catch (Exception ignored) {}
    }

    private void shutdownExecutor() {
        try { searchExecutor.shutdownNow(); } catch (Exception ignored) {}
    }
}
