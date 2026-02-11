package com.maze.mazeidea.build;

import com.maze.mazeidea.WorkspaceManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BuildToolController {
    @FXML private ChoiceBox<String> toolChoice;
    @FXML private TextField tasksField;
    @FXML private Button runButton;
    @FXML private Button cleanButton;
    @FXML private Button buildButton;
    @FXML private Button testButton;
    @FXML private Label statusLabel;
    @FXML private TextArea outputArea;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "build-tool"));

    @FXML
    public void initialize() {
        if (toolChoice != null) {
            toolChoice.getItems().setAll("Maven", "Gradle");
            toolChoice.setValue(detectDefaultTool());
        }
        if (runButton != null) runButton.setOnAction(e -> runTasks(getTasks()));
        if (cleanButton != null) cleanButton.setOnAction(e -> runTasks("clean"));
        if (buildButton != null) buildButton.setOnAction(e -> runTasks(defaultBuildTask()));
        if (testButton != null) testButton.setOnAction(e -> runTasks(defaultTestTask()));
    }

    private String detectDefaultTool() {
        Path root = WorkspaceManager.getWorkspaceRoot();
        if (root != null) {
            if (Files.exists(root.resolve("pom.xml"))) return "Maven";
            if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) return "Gradle";
        }
        return "Maven";
    }

    private String getTasks() {
        return tasksField != null ? tasksField.getText() : "";
    }

    private String defaultBuildTask() {
        return "Maven".equals(toolChoice.getValue()) ? "package" : "build";
    }

    private String defaultTestTask() {
        return "Maven".equals(toolChoice.getValue()) ? "test" : "test";
    }

    private void runTasks(String tasks) {
        Path root = WorkspaceManager.getWorkspaceRoot();
        if (root == null) {
            updateStatus("Open a workspace first.");
            return;
        }
        List<String> cmd = buildCommand(tasks);
        if (cmd.isEmpty()) {
            updateStatus("No build tool found.");
            return;
        }
        appendOutput("> " + String.join(" ", cmd));
        executor.submit(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(root.toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        appendOutput(line);
                    }
                }
                int code = p.waitFor();
                updateStatus("Finished (exit " + code + ")");
            } catch (Exception ex) {
                updateStatus("Build failed: " + ex.getMessage());
            }
        });
    }

    private List<String> buildCommand(String tasks) {
        List<String> cmd = new ArrayList<>();
        String tool = toolChoice != null ? toolChoice.getValue() : "Maven";
        Path root = WorkspaceManager.getWorkspaceRoot();
        if ("Gradle".equals(tool)) {
            if (root != null && Files.exists(root.resolve("gradlew"))) cmd.add("./gradlew");
            else cmd.add("gradle");
        } else {
            if (root != null && Files.exists(root.resolve("mvnw"))) cmd.add("./mvnw");
            else cmd.add("mvn");
        }
        if (tasks != null && !tasks.isBlank()) {
            for (String t : tasks.trim().split("\\s+")) cmd.add(t);
        }
        return cmd;
    }

    private void updateStatus(String text) {
        Platform.runLater(() -> {
            if (statusLabel != null) statusLabel.setText(text);
        });
    }

    private void appendOutput(String line) {
        Platform.runLater(() -> {
            if (outputArea != null) outputArea.appendText(line + System.lineSeparator());
        });
    }
}
