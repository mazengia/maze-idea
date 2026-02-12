package com.maze.mazeidea;

import com.maze.mazeidea.lsp.LspService;
import com.maze.mazeidea.util.Debouncer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainWindowController {
    @FXML public TreeView<java.nio.file.Path> projectTree;
    @FXML public TabPane editorTabs;
    @FXML public StackPane centerStack;
    @FXML public BorderPane drawerPane;
    @FXML public Label drawerTitle;
    @FXML public Button drawerCloseButton;
    @FXML public VBox projectPane;
    @FXML public ListView<String> toolList;
    @FXML public Label statusLabel;
    @FXML public Label healthStatusLabel;
    @FXML public MenuItem menuNewProject;
    @FXML public TabPane toolTabs;
    @FXML public Button toolProjectButton;
    @FXML public Button toolDatabaseButton;
    @FXML public Button toolRunButton;
    @FXML public Button toolBuildButton;
    @FXML public Button toolGitButton;
    @FXML public Button toolTerminalButton;
    @FXML public ChoiceBox<String> runConfigChoice;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "syntax-highlighter"));
    private final Debouncer debouncer = new Debouncer();
    private Stage runStage;
    private TextArea runConsole;
    private final Preferences prefs = Preferences.userNodeForPackage(MainWindowController.class);
    private final List<RunConfig> runConfigs = new ArrayList<>();
    private boolean runConfigListenerAdded = false;
    private boolean drawerOpen = false;
    private TranslateTransition drawerTransition;
    private boolean projectOpen = true;
    private TranslateTransition projectTransition;

    private static final String PREF_RUN_COUNT = "run.config.count";
    private static final String PREF_RUN_SELECTED = "run.config.selected";

    private static final String[] KEYWORDS = new String[] {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = ";";
    private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//[^\\n]*|/\\*(.|\\R)*?\\*/";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );

    @FXML
    public void initialize() {
        toolList.getItems().addAll("Search", "Git", "Problems");
        loadRunConfigs();
        setupDrawer();
        updateProjectHealth(WorkspaceManager.getWorkspaceRoot());

        TreeItem<java.nio.file.Path> rootNode = new TreeItem<>(null);
        projectTree.setRoot(rootNode);
        projectTree.getRoot().setExpanded(true);
        projectTree.setShowRoot(true);
        // show human-friendly names
        projectTree.setCellFactory(tv -> new TreeCell<java.nio.file.Path>() {
            @Override
            protected void updateItem(java.nio.file.Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else if (item == null) {
                    setText("Workspace");
                    setGraphic(createIconNode(null, IconKind.WORKSPACE));
                } else {
                    java.nio.file.Path fn = item.getFileName();
                    setText(fn != null ? fn.toString() : item.toString());
                    IconKind kind = IconKind.FILE;
                    try {
                        if (java.nio.file.Files.isDirectory(item)) kind = IconKind.FOLDER;
                        else if (fn != null && "pom.xml".equalsIgnoreCase(fn.toString())) kind = IconKind.POM;
                    } catch (Exception ignored) {}
                    setGraphic(createIconNode(item, kind));
                }
            }
        });

        projectTree.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                TreeItem<java.nio.file.Path> sel = projectTree.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getValue() != null) {
                    Path val = sel.getValue();
                    try {
                        if (java.nio.file.Files.isDirectory(val)) {
                            sel.setExpanded(!sel.isExpanded());
                        } else {
                            openFileInEditor(val);
                        }
                    } catch (Exception ex) {
                        // fallback: open as file
                        openFileInEditor(val);
                    }
                }
            }
        });

        // initial load from WorkspaceManager
        refreshProjectTree(WorkspaceManager.getWorkspaceRoot());
        // listen for workspace changes
        WorkspaceManager.addListener(root -> {
            // close all editor tabs and refresh tree
            javafx.application.Platform.runLater(() -> {
                editorTabs.getTabs().clear();
                statusLabel.setText("Workspace switched to: " + (root != null ? root.toString() : "(none)"));
                refreshProjectTree(root);
                refreshRunConfigsForWorkspace(root);
                updateProjectHealth(root);
            });
        });
    }

    private TreeItem<Path> createNode(Path file) {
        TreeItem<Path> item = new TreeItem<>(file);
        if (file != null && java.nio.file.Files.isDirectory(file)) {
            // placeholder child so the node shows expandable arrow
            item.getChildren().add(new TreeItem<>(null));
            item.expandedProperty().addListener((obs, was, isNow) -> {
                if (isNow) {
                    // populate on first expansion (placeholder check)
                    if (item.getChildren().size() == 1 && item.getChildren().get(0).getValue() == null) {
                        item.getChildren().clear();
                        try {
                            java.util.List<Path> kids = new java.util.ArrayList<>();
                            try (java.util.stream.Stream<Path> s = java.nio.file.Files.list(file)) {
                                s.sorted().forEach(kids::add);
                            }
                            for (Path k : kids) item.getChildren().add(createNode(k));
                        } catch (Exception ignored) {}
                    }
                }
            });
        }
        return item;
    }

    private void refreshProjectTree(Path root) {
        projectTree.getRoot().getChildren().clear();
        if (root == null) return;
        try {
            com.maze.mazeidea.project.ProjectImporter importer = new com.maze.mazeidea.project.ProjectImporter();
            com.maze.mazeidea.project.ProjectModel model = importer.importProjectModel(root);
            // create tree with lazy children
            TreeItem<Path> proj = createNode(root);
            // ensure top-level pom, modules, and source roots are visible quickly by expanding project node and adding those children
            proj.setExpanded(true);
            java.util.Set<Path> pinned = new java.util.LinkedHashSet<>();
            Path pom = root.resolve("pom.xml");
            if (pom.toFile().exists()) pinned.add(pom);
            for (String m : model.getModules()) pinned.add(root.resolve(m));
            pinned.addAll(model.getSourceRoots());
            for (Path p : pinned) proj.getChildren().add(createNode(p));
            projectTree.getRoot().getChildren().add(proj);
         } catch (Exception e) {
             // fallback: show user.dir
             try {
                 Path cwd = Path.of(System.getProperty("user.dir"));
                try (java.util.stream.Stream<Path> s = Files.list(cwd)) {
                    s.limit(200).sorted().forEach(p -> projectTree.getRoot().getChildren().add(createNode(p)));
                }
             } catch (IOException ignored) {}
         }
     }

    private void openFileInEditor(Path filePath) {
        final String tabName = (filePath.getFileName() != null) ? filePath.getFileName().toString() : filePath.toString();
        Tab t = new Tab(tabName);
        final CodeArea codeArea = new CodeArea();
        codeArea.getStyleClass().add("code-area");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setWrapText(false);
        codeArea.setPrefSize(800,600);

        // load file content
        try {
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                String text = Files.readString(filePath);
                codeArea.replaceText(0, 0, text);
                StyleSpans<Collection<String>> spans = computeHighlighting(text);
                codeArea.setStyleSpans(0, spans);
                com.maze.mazeidea.lsp.LspService lsp = ServiceManager.getLspService(com.maze.mazeidea.lsp.LspService.class);
                if (lsp != null) lsp.didOpen(filePath, text);
            }
        } catch (IOException ignored) {}

        // track dirty state
        final boolean[] dirty = new boolean[]{false};
        Runnable markDirty = () -> {
            if (!dirty[0]) {
                dirty[0] = true;
                javafx.application.Platform.runLater(() -> t.setText(tabName + " *"));
            }
        };

        // Debounced syntax highlighting (300ms) and mark dirty
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            markDirty.run();
            debouncer.debounce(() -> {
                StyleSpans<Collection<String>> spans = computeHighlighting(newText);
                javafx.application.Platform.runLater(() -> codeArea.setStyleSpans(0, spans));
                // notify LSP of the change (debounced)
                com.maze.mazeidea.lsp.LspService lsp = ServiceManager.getLspService(com.maze.mazeidea.lsp.LspService.class);
                if (lsp != null) lsp.didChange(filePath, newText);
            }, 300);
        });

        // Save function
        Runnable save = () -> {
            try {
                Files.writeString(filePath, codeArea.getText());
                dirty[0] = false;
                javafx.application.Platform.runLater(() -> t.setText(tabName));
                if (ServiceManager.isInitialized() && ServiceManager.getIndexer() != null) {
                    ServiceManager.getIndexer().indexFileAsync(filePath);
                }
            } catch (IOException e) {
                javafx.application.Platform.runLater(() -> statusLabel.setText("Save failed: " + e.getMessage()));
            }
        };

        // Save on Ctrl+S and index the file
        codeArea.addEventHandler(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.isControlDown() && ev.getCode() == KeyCode.S) {
                save.run();
                ev.consume();
            }

            // Basic auto-complete on Ctrl+Space
            if (ev.isControlDown() && ev.getCode() == KeyCode.SPACE) {
                showSimpleCompletion(codeArea, filePath);
                ev.consume();
            }

            // Auto-indent on Enter
            if (ev.getCode() == KeyCode.ENTER) {
                int par = codeArea.getCurrentParagraph();
                String prev = codeArea.getParagraph(par > 0 ? par - 1 : 0).getText();
                String indent = getIndentFromLine(prev);
                javafx.application.Platform.runLater(() -> codeArea.insertText(codeArea.getCaretPosition(), indent));
            }

            // Auto-pair brackets
            if (!ev.isControlDown()) {
                if (ev.getText() != null && ev.getText().length() == 1) {
                    char c = ev.getText().charAt(0);
                    char pair = 0;
                    if (c == '(') pair = ')';
                    else if (c == '{') pair = '}';
                    else if (c == '[') pair = ']';
                    else if (c == '\'') pair = '\'';
                    else if (c == '"') pair = '"';
                    if (pair != 0) {
                        int pos = codeArea.getCaretPosition();
                        final int posAt = pos;
                        final char pairAt = pair;
                        javafx.application.Platform.runLater(() -> codeArea.insertText(posAt, String.valueOf(pairAt)));
                    }
                }
            }
        });

        // Save on focus lost
        codeArea.focusedProperty().addListener((obs, oldF, newF) -> { if (!newF && dirty[0]) save.run(); });

        // Bracket matching: highlight matching bracket for caret
        codeArea.caretPositionProperty().addListener((obs, oldP, newP) -> {
            int pos = newP.intValue();
            // naive check: highlight the char at pos-1 if it's a bracket and try to find matching
            try {
                if (pos > 0) {
                    char ch = codeArea.getText().charAt(pos-1);
                    Optional<Integer> match = findMatchingBracket(codeArea.getText(), pos-1);
                    if (match.isPresent()) {
                        // simple visual feedback by selecting the bracket pair briefly
                        int m = match.get();
                        javafx.application.Platform.runLater(() -> {
                            codeArea.selectRange(m, m+1);
                            codeArea.deselect();
                        });
                    }
                }
            } catch (Exception ignored) {}
        });

        t.setContent(codeArea);
        editorTabs.getTabs().add(t);
        editorTabs.getSelectionModel().select(t);
        statusLabel.setText("Opened " + filePath);
    }

    private void showSimpleCompletion(CodeArea area, Path filePath) {
        // try LSP completions first
        com.maze.mazeidea.lsp.LspService lsp = ServiceManager.getLspService(com.maze.mazeidea.lsp.LspService.class);
        String[] suggestions = null;
        if (lsp != null) {
            int offset = area.getCaretPosition();
            String prefix = getCompletionPrefix(area);
            suggestions = lsp.complete(filePath, offset, prefix, area.getText());
        }
        if (suggestions == null) {
            suggestions = KEYWORDS;
        }

        ContextMenu menu = new ContextMenu();
        List<MenuItem> items = new ArrayList<>();
        for (String s : suggestions) {
            MenuItem it = new MenuItem(s);
            it.setOnAction(e -> insertCompletion(area, s));
            items.add(it);
        }
        menu.getItems().addAll(items);
        area.getCaretBounds().ifPresent(bounds -> {
            javafx.geometry.Point2D p = area.localToScreen(bounds.getMinX(), bounds.getMinY());
            menu.show(area, p.getX(), p.getY());

            // accept suggestion on Tab or Enter: pick first item if present
            area.addEventFilter(KeyEvent.KEY_PRESSED, ke -> {
                if ((ke.getCode() == KeyCode.TAB || ke.getCode() == KeyCode.ENTER) && !menu.getItems().isEmpty()) {
                    MenuItem first = menu.getItems().get(0);
                    first.fire();
                    menu.hide();
                    ke.consume();
                }
                // Escape to hide
                if (ke.getCode() == KeyCode.ESCAPE) { menu.hide(); ke.consume(); }
            });
        });
    }

    private static String getCompletionPrefix(CodeArea area) {
        int pos = area.getCaretPosition();
        int start = pos - 1;
        String text = area.getText();
        while (start >= 0) {
            char c = text.charAt(start);
            if (!Character.isJavaIdentifierPart(c)) break;
            start--;
        }
        return text.substring(start+1, pos);
    }

    private static void insertCompletion(CodeArea area, String completion) {
        int pos = area.getCaretPosition();
        int start = pos - 1;
        String text = area.getText();
        while (start >= 0) {
            char c = text.charAt(start);
            if (!Character.isJavaIdentifierPart(c)) break;
            start--;
        }
        int replaceFrom = start + 1;
        javafx.application.Platform.runLater(() -> {
            area.replaceText(replaceFrom, pos, completion);
        });
    }

    private static String getIndentFromLine(String line) {
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') sb.append(c);
            else break;
        }
        return sb.toString();
    }

    private Optional<Integer> findMatchingBracket(String text, int pos) {
        char ch = text.charAt(pos);
        char match;
        int dir;
        if (ch == '(') { match = ')'; dir = 1; }
        else if (ch == ')') { match = '('; dir = -1; }
        else if (ch == '{') { match = '}'; dir = 1; }
        else if (ch == '}') { match = '{'; dir = -1; }
        else if (ch == '[') { match = ']'; dir = 1; }
        else if (ch == ']') { match = '['; dir = -1; }
        else return Optional.empty();

        int depth = 0;
        int i = pos;
        while (i >= 0 && i < text.length()) {
            char c = text.charAt(i);
            if (c == ch) depth++;
            if (c == match) depth--;
            if (depth == 0 && i != pos) return Optional.of(i);
            i += dir;
        }
        return Optional.empty();
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("PAREN") != null ? "paren" :
                                    matcher.group("BRACE") != null ? "brace" :
                                            matcher.group("BRACKET") != null ? "bracket" :
                                                    matcher.group("SEMICOLON") != null ? "semicolon" :
                                                            matcher.group("STRING") != null ? "string" :
                                                                    matcher.group("COMMENT") != null ? "comment" : null;
            assert styleClass != null;
            spansBuilder.add(java.util.Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(java.util.Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(java.util.Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    @FXML
    public void onNewProject() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-project.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("ide-theme.css").toExternalForm());
            Stage stage = new Stage();
            stage.setTitle("New Project");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void onOpenProject() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Open Project Directory");
        java.io.File dir = chooser.showDialog(null);
        if (dir != null && dir.exists() && dir.isDirectory()) {
            Path p = dir.toPath();
            WorkspaceManager.setWorkspace(p);
            ServiceManager.switchWorkspace(p);
            refreshProjectTree(p);
        }
    }

    @FXML
    public void onSearch() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("search-window.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("ide-theme.css").toExternalForm());
            Stage stage = new Stage();
            stage.setTitle("Search");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void onRun() {
        runSelected(false);
    }

    @FXML
    public void onDebug() {
        runSelected(true);
    }

    @FXML
    public void onEditRunConfigs() {
        showRunConfigDialog();
    }

    @FXML
    public void onToolProject() {
        toggleProjectPane();
    }

    @FXML
    public void onToolDatabase() {
        toggleDrawerFor("Database");
    }

    @FXML
    public void onToolRun() {
        toggleDrawerFor("Run");
    }

    @FXML
    public void onToolBuild() {
        toggleDrawerFor("Build");
    }

    @FXML
    public void onToolGit() {
        toggleDrawerFor("Git");
    }

    @FXML
    public void onToolTerminal() {
        selectToolTab("Terminal");
    }

    private TextArea ensureRunConsole() {
        if (runStage != null && runStage.isShowing() && runConsole != null) {
            runStage.toFront();
            return runConsole;
        }

        runConsole = new TextArea();
        runConsole.setEditable(false);
        runConsole.setWrapText(false);
        runConsole.getStyleClass().add("run-console");

        Scene scene = new Scene(new VBox(runConsole), 900, 300);
        scene.getStylesheets().add(getClass().getResource("ide-theme.css").toExternalForm());
        Stage stage = new Stage();
        stage.setTitle("Run Console");
        stage.setScene(scene);
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> {
            runStage = null;
            runConsole = null;
        });
        stage.show();
        runStage = stage;
        return runConsole;
    }

    private void runSelected(boolean debug) {
        RunConfig cfg = getSelectedRunConfig();
        if (cfg == null) {
            statusLabel.setText("No run configuration selected.");
            return;
        }
        Path workDir = resolveWorkingDir(cfg);
        if (workDir == null) {
            statusLabel.setText("Invalid working directory.");
            return;
        }

        List<String> cmd = buildCommand(cfg);
        if (cmd.isEmpty()) {
            statusLabel.setText("Run configuration is missing a command.");
            return;
        }

        TextArea console = ensureRunConsole();
        console.appendText("> " + String.join(" ", cmd) + System.lineSeparator());
        statusLabel.setText((debug ? "Debugging " : "Running ") + cfg.name + "...");

        Thread t = new Thread(() -> {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            if (debug) {
                if (cfg.type == RunType.SPRING_BOOT) {
                    pb.environment().put("JAVA_TOOL_OPTIONS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005");
                } else if (cfg.type == RunType.NODE || cfg.type == RunType.ANGULAR) {
                    pb.environment().put("NODE_OPTIONS", "--inspect=9229");
                }
            }
            try {
                Process p = pb.start();
                try (InputStream is = p.getInputStream();
                     BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String out = line + System.lineSeparator();
                        javafx.application.Platform.runLater(() -> console.appendText(out));
                    }
                }
                int code = p.waitFor();
                javafx.application.Platform.runLater(() -> statusLabel.setText("Run finished (exit " + code + ")."));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    console.appendText("Run failed: " + e.getMessage() + System.lineSeparator());
                    statusLabel.setText("Run failed.");
                });
            }
        }, "run-project");
        t.setDaemon(true);
        t.start();
    }

    private RunConfig getSelectedRunConfig() {
        if (runConfigChoice == null || runConfigs.isEmpty()) return null;
        String name = runConfigChoice.getValue();
        if (name == null) return runConfigs.get(0);
        for (RunConfig c : runConfigs) {
            if (name.equals(c.name)) return c;
        }
        return runConfigs.get(0);
    }

    private Path resolveWorkingDir(RunConfig cfg) {
        String dir = cfg.workingDir;
        if (dir == null || dir.isBlank()) {
            Path root = WorkspaceManager.getWorkspaceRoot();
            return root != null ? root : null;
        }
        try {
            return Path.of(dir);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> buildCommand(RunConfig cfg) {
        String cmd = cfg.command;
        String args = cfg.args;
        List<String> list = new ArrayList<>();
        if (cmd == null || cmd.isBlank()) {
            return buildDefaultCommand(cfg);
        }
        list.add(cmd.trim());
        list.addAll(parseArgs(args));
        return list;
    }

    private List<String> buildDefaultCommand(RunConfig cfg) {
        List<String> list = new ArrayList<>();
        if (cfg.type == RunType.SPRING_BOOT) {
            list.add("mvn");
            list.add("spring-boot:run");
        } else if (cfg.type == RunType.ANGULAR) {
            String ng = resolveExecutable("ng");
            if (ng != null) {
                list.add(ng);
                list.add("serve");
            } else {
                list.add("npm");
                list.add("start");
            }
        } else if (cfg.type == RunType.NODE) {
            list.add("npm");
            list.add("run");
            list.add("start");
        }
        return list;
    }

    private List<String> parseArgs(String args) {
        if (args == null || args.isBlank()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void loadRunConfigs() {
        runConfigs.clear();
        int count = prefs.getInt(PREF_RUN_COUNT, 0);
        for (int i = 0; i < count; i++) {
            String name = prefs.get("run.config." + i + ".name", null);
            String type = prefs.get("run.config." + i + ".type", RunType.CUSTOM.name());
            String cmd = prefs.get("run.config." + i + ".cmd", "");
            String args = prefs.get("run.config." + i + ".args", "");
            String dir = prefs.get("run.config." + i + ".dir", "");
            if (name != null) {
                runConfigs.add(new RunConfig(name, RunType.valueOf(type), cmd, args, dir));
            }
        }
        if (runConfigs.isEmpty()) {
            Path root = WorkspaceManager.getWorkspaceRoot();
            runConfigs.addAll(detectDefaultConfigs(root));
        } else {
            ensureDefaultConfigs(WorkspaceManager.getWorkspaceRoot());
        }
        refreshRunConfigChoice();
    }

    private void refreshRunConfigsForWorkspace(Path root) {
        ensureDefaultConfigs(root);
        refreshRunConfigChoice();
    }

    private void saveRunConfigs() {
        prefs.putInt(PREF_RUN_COUNT, runConfigs.size());
        for (int i = 0; i < runConfigs.size(); i++) {
            RunConfig c = runConfigs.get(i);
            prefs.put("run.config." + i + ".name", c.name);
            prefs.put("run.config." + i + ".type", c.type.name());
            prefs.put("run.config." + i + ".cmd", c.command == null ? "" : c.command);
            prefs.put("run.config." + i + ".args", c.args == null ? "" : c.args);
            prefs.put("run.config." + i + ".dir", c.workingDir == null ? "" : c.workingDir);
        }
        if (runConfigChoice != null && runConfigChoice.getValue() != null) {
            prefs.put(PREF_RUN_SELECTED, runConfigChoice.getValue());
        }
    }

    private void refreshRunConfigChoice() {
        if (runConfigChoice == null) return;
        List<String> names = runConfigs.stream().map(c -> c.name).collect(Collectors.toList());
        runConfigChoice.getItems().setAll(names);
        String selected = prefs.get(PREF_RUN_SELECTED, null);
        if (selected != null && names.contains(selected)) runConfigChoice.setValue(selected);
        else if (!names.isEmpty()) runConfigChoice.setValue(names.get(0));
        if (!runConfigListenerAdded) {
            runConfigChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) prefs.put(PREF_RUN_SELECTED, newV);
            });
            runConfigListenerAdded = true;
        }
    }

    private List<RunConfig> detectDefaultConfigs(Path root) {
        List<RunConfig> out = new ArrayList<>();
        if (root == null) return out;
        if (isSpringBootProject(root)) {
            out.add(new RunConfig("Spring Boot", RunType.SPRING_BOOT, "mvn", "spring-boot:run", root.toString()));
        }
        if (Files.exists(root.resolve("angular.json"))) {
            out.add(new RunConfig("Angular", RunType.ANGULAR, "", "", root.toString()));
        }
        if (isReactProject(root) && out.stream().noneMatch(c -> c.type == RunType.ANGULAR)) {
            out.add(new RunConfig("React", RunType.NODE, "", "", root.toString()));
        } else if (Files.exists(root.resolve("package.json")) && out.stream().noneMatch(c -> c.type == RunType.ANGULAR)) {
            out.add(new RunConfig("Node", RunType.NODE, "", "", root.toString()));
        }
        return out;
    }

    private void ensureDefaultConfigs(Path root) {
        if (root == null) return;
        if (isSpringBootProject(root) && !hasRunConfig("Spring Boot")) {
            runConfigs.add(new RunConfig("Spring Boot", RunType.SPRING_BOOT, "mvn", "spring-boot:run", root.toString()));
        }
        if (Files.exists(root.resolve("angular.json")) && !hasRunConfig("Angular")) {
            runConfigs.add(new RunConfig("Angular", RunType.ANGULAR, "", "", root.toString()));
        }
        if (isReactProject(root) && !hasRunConfig("React")) {
            runConfigs.add(new RunConfig("React", RunType.NODE, "", "", root.toString()));
        } else if (Files.exists(root.resolve("package.json")) && !hasRunConfig("Node")) {
            runConfigs.add(new RunConfig("Node", RunType.NODE, "", "", root.toString()));
        }
    }

    private boolean hasRunConfig(String name) {
        for (RunConfig c : runConfigs) {
            if (name.equalsIgnoreCase(c.name)) return true;
        }
        return false;
    }

    private boolean isSpringBootProject(Path root) {
        if (root == null || !Files.exists(root.resolve("pom.xml"))) return false;
        Path src = root.resolve("src/main/java");
        if (!Files.exists(src)) return false;
        try (java.util.stream.Stream<Path> s = Files.walk(src)) {
            return s.filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(this::containsSpringBootAnnotation);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean containsSpringBootAnnotation(Path file) {
        try {
            String text = Files.readString(file);
            return text.contains("@SpringBootApplication");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isReactProject(Path root) {
        Path pkg = root != null ? root.resolve("package.json") : null;
        if (pkg == null || !Files.exists(pkg)) return false;
        try {
            String text = Files.readString(pkg);
            return text.contains("\"react\"") || text.contains("react-scripts");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateProjectHealth(Path root) {
        if (healthStatusLabel == null) return;
        boolean spring = isSpringBootProject(root);
        boolean angular = root != null && Files.exists(root.resolve("angular.json"));
        boolean react = isReactProject(root);
        if (spring || angular || react) {
            String label = spring ? "Spring Boot ready" : (angular ? "Angular ready" : "React ready");
            setHealthStatus(label, "mdi2c-cloud-check", "status-ok");
        } else if (root != null && Files.exists(root.resolve("pom.xml"))) {
            setHealthStatus("No Spring Boot app", "mdi2c-cloud-alert", "status-warn");
        } else if (root != null && Files.exists(root.resolve("package.json"))) {
            setHealthStatus("No runnable app", "mdi2c-cloud-alert", "status-warn");
        } else {
            setHealthStatus("No project detected", "mdi2c-cloud-alert", "status-warn");
        }
    }

    private void setHealthStatus(String text, String iconLiteral, String statusClass) {
        javafx.application.Platform.runLater(() -> {
            healthStatusLabel.setText(text);
            healthStatusLabel.getStyleClass().removeAll("status-ok", "status-warn", "status-error");
            healthStatusLabel.getStyleClass().add(statusClass);
            if (iconLiteral != null) {
                FontIcon icon = new FontIcon(iconLiteral);
                icon.getStyleClass().add("status-icon");
                healthStatusLabel.setGraphic(icon);
            }
        });
    }

    private String resolveExecutable(String command) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return null;
        String[] dirs = path.split(java.io.File.pathSeparator);
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String[] exts = isWindows ? new String[] {".exe", ".cmd", ".bat"} : new String[] {""};
        for (String dir : dirs) {
            if (dir == null || dir.isBlank()) continue;
            for (String ext : exts) {
                Path p = Path.of(dir, command + ext);
                if (Files.exists(p) && Files.isRegularFile(p) && Files.isExecutable(p)) {
                    return p.toString();
                }
            }
        }
        return null;
    }

    private void showRunConfigDialog() {
        Stage stage = new Stage();
        stage.setTitle("Run/Debug Configurations");
        ObservableList<RunConfig> items = FXCollections.observableArrayList(runConfigs);
        ListView<RunConfig> list = new ListView<>(items);

        TextField nameField = new TextField();
        ChoiceBox<RunType> typeChoice = new ChoiceBox<>(FXCollections.observableArrayList(RunType.values()));
        TextField cmdField = new TextField();
        TextField argsField = new TextField();
        TextField dirField = new TextField();
        Button browse = new Button("Browse...");

        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            nameField.setText(newV.name);
            typeChoice.setValue(newV.type);
            cmdField.setText(newV.command);
            argsField.setText(newV.args);
            dirField.setText(newV.workingDir);
        });

        Button add = new Button("Add");
        Button remove = new Button("Remove");
        Button save = new Button("Save");
        Button close = new Button("Close");

        add.setOnAction(e -> {
            RunConfig c = new RunConfig("New Config", RunType.CUSTOM, "", "", defaultWorkingDir());
            items.add(c);
            list.getSelectionModel().select(c);
        });
        remove.setOnAction(e -> {
            RunConfig c = list.getSelectionModel().getSelectedItem();
            if (c != null) items.remove(c);
        });
        save.setOnAction(e -> {
            RunConfig c = list.getSelectionModel().getSelectedItem();
            if (c == null) return;
            c.name = nameField.getText();
            c.type = typeChoice.getValue() != null ? typeChoice.getValue() : RunType.CUSTOM;
            c.command = cmdField.getText();
            c.args = argsField.getText();
            c.workingDir = dirField.getText();
            list.refresh();
            runConfigs.clear();
            runConfigs.addAll(items);
            saveRunConfigs();
            refreshRunConfigChoice();
        });
        close.setOnAction(e -> stage.close());

        browse.setOnAction(e -> {
            javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
            dc.setTitle("Select Working Directory");
            java.io.File chosen = dc.showDialog(stage);
            if (chosen != null && chosen.exists() && chosen.isDirectory()) {
                dirField.setText(chosen.getAbsolutePath());
            }
        });

        if (!items.isEmpty()) list.getSelectionModel().select(0);
        if (typeChoice.getValue() == null) typeChoice.setValue(RunType.CUSTOM);

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.add(new Label("Name:"), 0, 0);
        form.add(nameField, 1, 0);
        form.add(new Label("Type:"), 0, 1);
        form.add(typeChoice, 1, 1);
        form.add(new Label("Command:"), 0, 2);
        form.add(cmdField, 1, 2);
        form.add(new Label("Arguments:"), 0, 3);
        form.add(argsField, 1, 3);
        form.add(new Label("Working Dir:"), 0, 4);
        HBox dirRow = new HBox(8, dirField, browse);
        HBox.setHgrow(dirField, Priority.ALWAYS);
        form.add(dirRow, 1, 4);

        HBox buttons = new HBox(8, add, remove, save, close);
        VBox root = new VBox(10, new HBox(10, list, form), buttons);
        root.setStyle("-fx-padding: 10;");
        Scene scene = new Scene(root, 780, 360);
        scene.getStylesheets().add(getClass().getResource("ide-theme.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private String defaultWorkingDir() {
        Path root = WorkspaceManager.getWorkspaceRoot();
        return root != null ? root.toString() : System.getProperty("user.dir");
    }

    @FXML
    public void onExit() {
        debouncer.shutdown();
        executor.shutdownNow();
        System.exit(0);
    }

    // simple icon kinds to decide icon glyph
    private enum IconKind { FOLDER, FILE, POM, WORKSPACE }

    // Create a Node to use as icon. Try to use Ikonli FontIcon via reflection; fall back to an emoji Label.
    private javafx.scene.Node createIconNode(Path p, IconKind kind) {
        String code;
        switch (kind) {
            case FOLDER: code = "mdi2f-folder"; break;
            case POM: code = "mdi2f-file-xml"; break;
            case WORKSPACE: code = "mdi2f-view-dashboard"; break;
            case FILE:
            default: code = "mdi2f-file"; break;
        }

        // Attempt Ikonli FontIcon via reflection to avoid hard compile dependency
        try {
            Class<?> fontIconClass = Class.forName("org.kordamp.ikonli.javafx.FontIcon");
            java.lang.reflect.Constructor<?> ctor = fontIconClass.getConstructor(String.class);
            Object fontIcon = ctor.newInstance(code);
            if (fontIcon instanceof javafx.scene.Node) {
                // apply a small size if possible
                try {
                    java.lang.reflect.Method setIconSize = fontIconClass.getMethod("setIconSize", int.class);
                    setIconSize.invoke(fontIcon, 14);
                } catch (Exception ignored) {}
                return (javafx.scene.Node) fontIcon;
            }
        } catch (Throwable ignored) {
            // fallback to emoji
        }

        String emoji;
        switch (kind) {
            case FOLDER: emoji = "📁"; break;
            case POM: emoji = "📦"; break;
            case WORKSPACE: emoji = "🗂️"; break;
            default: emoji = "📄"; break;
        }
        Label l = new Label(emoji);
        l.setMinWidth(18);
        l.setMaxWidth(18);
        l.setStyle("-fx-font-size:12px;");
        return l;
    }

    private void selectToolTab(String name) {
        if (toolTabs == null) return;
        for (Tab t : toolTabs.getTabs()) {
            if (name.equalsIgnoreCase(t.getText())) {
                toolTabs.getSelectionModel().select(t);
                return;
            }
        }
    }

    private void setupDrawer() {
        if (drawerPane == null) return;
        drawerPane.setVisible(false);
        drawerPane.setManaged(true);
        drawerPane.setTranslateX(drawerPane.getPrefWidth() > 0 ? drawerPane.getPrefWidth() : getDrawerWidth());
        if (drawerCloseButton != null) drawerCloseButton.setOnAction(e -> setDrawerOpen(false));
        if (toolTabs != null) {
            toolTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && drawerTitle != null) drawerTitle.setText(newV.getText());
            });
        }
    }

    private void toggleProjectPane() {
        if (projectPane == null) return;
        if (projectTransition != null) projectTransition.stop();
        double width = getProjectWidth();
        projectTransition = new TranslateTransition(Duration.millis(160), projectPane);
        if (projectOpen) {
            projectTransition.setToX(-width);
            projectTransition.setOnFinished(e -> {
                projectPane.setVisible(false);
                projectPane.setManaged(false);
                projectOpen = false;
            });
        } else {
            projectPane.setManaged(true);
            projectPane.setVisible(true);
            projectPane.setTranslateX(-width);
            projectTransition.setToX(0);
            projectTransition.setOnFinished(e -> projectOpen = true);
            if (projectTree != null) projectTree.requestFocus();
        }
        projectTransition.playFromStart();
    }

    private double getProjectWidth() {
        if (projectPane == null) return 260;
        double width = projectPane.getWidth();
        if (width <= 0) width = projectPane.getPrefWidth();
        if (width <= 0) width = 260;
        return width;
    }

    private void toggleDrawerFor(String tabName) {
        String current = toolTabs != null && toolTabs.getSelectionModel().getSelectedItem() != null
                ? toolTabs.getSelectionModel().getSelectedItem().getText()
                : null;
        if (drawerOpen && current != null && current.equalsIgnoreCase(tabName)) {
            setDrawerOpen(false);
            return;
        }
        selectToolTab(tabName);
        setDrawerOpen(true);
    }

    private void setDrawerOpen(boolean open) {
        if (drawerPane == null) return;
        if (drawerTransition != null) drawerTransition.stop();
        double width = getDrawerWidth();
        drawerTransition = new TranslateTransition(Duration.millis(180), drawerPane);
        if (open) {
            drawerPane.setVisible(true);
            drawerPane.setManaged(true);
            drawerPane.setTranslateX(width);
            drawerTransition.setToX(0);
            drawerTransition.setOnFinished(e -> drawerOpen = true);
        } else {
            drawerTransition.setToX(width);
            drawerTransition.setOnFinished(e -> {
                drawerPane.setVisible(false);
                drawerPane.setManaged(true);
                drawerOpen = false;
            });
        }
        drawerTransition.playFromStart();
    }

    private double getDrawerWidth() {
        if (drawerPane == null) return 360;
        double width = drawerPane.getWidth();
        if (width <= 0) width = drawerPane.getPrefWidth();
        if (width <= 0) width = 360;
        return width;
    }

    private enum RunType { SPRING_BOOT, NODE, ANGULAR, CUSTOM }

    private static class RunConfig {
        String name;
        RunType type;
        String command;
        String args;
        String workingDir;

        RunConfig(String name, RunType type, String command, String args, String workingDir) {
            this.name = name;
            this.type = type;
            this.command = command;
            this.args = args;
            this.workingDir = workingDir;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
