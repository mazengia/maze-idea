package com.maze.mazeidea.terminal;

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

public class TerminalToolController {
    @FXML private TextArea terminalOutput;
    @FXML private TextField terminalInput;
    @FXML private Button runButton;
    @FXML private Button clearButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "terminal-tool"));

    @FXML
    public void initialize() {
        if (runButton != null) runButton.setOnAction(e -> runCommand());
        if (clearButton != null) clearButton.setOnAction(e -> clear());
        if (terminalInput != null) {
            terminalInput.setOnAction(e -> runCommand());
        }
    }

    private void runCommand() {
        String cmd = terminalInput != null ? terminalInput.getText() : "";
        if (cmd == null || cmd.isBlank()) return;
        Path root = WorkspaceManager.getWorkspaceRoot();
        appendOutput("> " + cmd);
        if (terminalInput != null) terminalInput.clear();

        executor.submit(() -> {
            try {
                List<String> shell = buildShellCommand(cmd);
                ProcessBuilder pb = new ProcessBuilder(shell);
                if (root != null) pb.directory(root.toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        appendOutput(line);
                    }
                }
                int code = p.waitFor();
                appendOutput("(exit " + code + ")");
            } catch (Exception ex) {
                appendOutput("Error: " + ex.getMessage());
            }
        });
    }

    private List<String> buildShellCommand(String cmd) {
        List<String> list = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            list.add("cmd");
            list.add("/c");
        } else {
            list.add("bash");
            list.add("-lc");
        }
        list.add(cmd);
        return list;
    }

    private void clear() {
        if (terminalOutput != null) terminalOutput.clear();
    }

    private void appendOutput(String line) {
        Platform.runLater(() -> {
            if (terminalOutput != null) terminalOutput.appendText(line + System.lineSeparator());
        });
    }
}
