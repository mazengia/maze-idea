package com.maze.mazeidea;

import com.maze.mazeidea.lsp.LspService;
import com.maze.mazeidea.util.Debouncer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainWindowController {
    @FXML public TreeView<java.nio.file.Path> projectTree;
    @FXML public TabPane editorTabs;
    @FXML public ListView<String> toolList;
    @FXML public Label statusLabel;
    @FXML public MenuItem menuNewProject;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "syntax-highlighter"));
    private final Debouncer debouncer = new Debouncer();

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

        TreeItem<java.nio.file.Path> rootNode = new TreeItem<>(null);
        projectTree.setRoot(rootNode);
        projectTree.getRoot().setExpanded(true);
        projectTree.setShowRoot(true);
        // show human-friendly names
        projectTree.setCellFactory(tv -> new TreeCell<java.nio.file.Path>() {
            @Override
            protected void updateItem(java.nio.file.Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(empty ? null : "Workspace");
                } else {
                    java.nio.file.Path fn = item.getFileName();
                    setText(fn != null ? fn.toString() : item.toString());
                }
            }
        });

        projectTree.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                TreeItem<java.nio.file.Path> sel = projectTree.getSelectionModel().getSelectedItem();
                if (sel != null && sel.isLeaf() && sel.getValue() != null) {
                    openFileInEditor(sel.getValue());
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
            });
        });
    }

    private void refreshProjectTree(Path root) {
        projectTree.getRoot().getChildren().clear();
        if (root == null) return;
        try {
            com.maze.mazeidea.project.ProjectImporter importer = new com.maze.mazeidea.project.ProjectImporter();
            com.maze.mazeidea.project.ProjectModel model = importer.importProjectModel(root);
            TreeItem<java.nio.file.Path> proj = new TreeItem<>(root);
            // show pom
            if (root.resolve("pom.xml").toFile().exists()) {
                TreeItem<java.nio.file.Path> pom = new TreeItem<>(root.resolve("pom.xml"));
                proj.getChildren().add(pom);
            }
            // modules
            for (String m : model.getModules()) {
                TreeItem<java.nio.file.Path> mod = new TreeItem<>(root.resolve(m));
                proj.getChildren().add(mod);
            }
            // source roots
            for (java.nio.file.Path sr : model.getSourceRoots()) {
                TreeItem<java.nio.file.Path> s = new TreeItem<>(sr);
                proj.getChildren().add(s);
            }
            projectTree.getRoot().getChildren().add(proj);
         } catch (Exception e) {
             // fallback: show user.dir
             try {
                 Path cwd = Path.of(System.getProperty("user.dir"));
                Files.list(cwd).limit(200).forEach(p -> projectTree.getRoot().getChildren().add(new TreeItem<>(p)));
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
            suggestions = lsp.complete(filePath, offset, prefix);
        }

        ContextMenu menu = new ContextMenu();
        List<MenuItem> items = new ArrayList<>();
        if (suggestions != null) {
            for (String s : suggestions) {
                MenuItem it = new MenuItem(s);
                it.setOnAction(e -> insertCompletion(area, s));
                items.add(it);
            }
        } else {
            for (String k : KEYWORDS) {
                MenuItem it = new MenuItem(k);
                it.setOnAction(e -> insertCompletion(area, k));
                items.add(it);
            }
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
            Stage stage = new Stage();
            stage.setTitle("Search");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }

    @FXML
    public void onExit() {
        debouncer.shutdown();
        executor.shutdownNow();
        System.exit(0);
    }

}
