package com.maze.mazeidea.git;

import com.maze.mazeidea.WorkspaceManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitToolController {
    @FXML private Button refreshButton;
    @FXML private ListView<String> statusList;
    @FXML private TextArea diffArea;
    @FXML private TextArea commitMessage;
    @FXML private Button commitButton;
    @FXML private Label commitStatus;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "git-tool"));

    @FXML
    public void initialize() {
        if (refreshButton != null) refreshButton.setOnAction(e -> refreshStatus());
        if (commitButton != null) commitButton.setOnAction(e -> commitAll());
        if (statusList != null) {
            statusList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) showDiffFor(newV);
            });
        }
        refreshStatus();
    }

    private void refreshStatus() {
        Path root = WorkspaceManager.getWorkspaceRoot();
        if (root == null) {
            setCommitStatus("Open a workspace.");
            return;
        }
        executor.submit(() -> {
            List<String> lines = runGit(root, "status", "--porcelain");
            Platform.runLater(() -> {
                if (statusList != null) statusList.getItems().setAll(lines);
                if (diffArea != null) diffArea.clear();
                setCommitStatus(lines.isEmpty() ? "Working tree clean" : "");
            });
        });
    }

    private void showDiffFor(String statusLine) {
        Path root = WorkspaceManager.getWorkspaceRoot();
        if (root == null || statusLine == null || statusLine.length() < 4) return;
        String file = statusLine.substring(3).trim();
        executor.submit(() -> {
            List<String> diff = runGit(root, "diff", "--", file);
            Platform.runLater(() -> {
                if (diffArea != null) diffArea.setText(String.join(System.lineSeparator(), diff));
            });
        });
    }

    private void commitAll() {
        Path root = WorkspaceManager.getWorkspaceRoot();
        if (root == null) {
            setCommitStatus("Open a workspace.");
            return;
        }
        String msg = commitMessage != null ? commitMessage.getText() : "";
        if (msg == null || msg.isBlank()) {
            setCommitStatus("Enter a commit message.");
            return;
        }
        executor.submit(() -> {
            runGit(root, "add", "-A");
            List<String> out = runGit(root, "commit", "-m", msg);
            Platform.runLater(() -> {
                setCommitStatus(String.join(" ", out));
                if (commitMessage != null) commitMessage.clear();
                refreshStatus();
            });
        });
    }

    private List<String> runGit(Path root, String... args) {
        List<String> out = new ArrayList<>();
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            for (String a : args) cmd.add(a);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.add(line);
            }
            p.waitFor();
        } catch (Exception ex) {
            out.add("Error: " + ex.getMessage());
        }
        return out;
    }

    private void setCommitStatus(String text) {
        Platform.runLater(() -> {
            if (commitStatus != null) commitStatus.setText(text);
        });
    }
}
