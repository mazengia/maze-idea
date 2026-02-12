package com.maze.mazeidea;

import com.maze.mazeidea.project.ProjectGenerator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.concurrent.TimeUnit;

public class NewProjectWindowController {
    // Wizard pages
    @FXML public StackPane wizardStack;
    @FXML public Node templatePage;
    @FXML public Node detailsPage;

    // Template page controls
    @FXML public Button chooseSpringBtn;
    @FXML public Button chooseReactBtn;
    @FXML public Button chooseAngularBtn;

    // Details page
    @FXML public TextField projectNameField;
    @FXML public Label projectNameLabel;
    @FXML public TextField serverUrlField;
    @FXML public ChoiceBox<String> languageChoice;
    @FXML public ChoiceBox<String> typeChoice;
    @FXML public CheckBox createGitCheck;
    @FXML public TextField groupIdField;
    @FXML public Label groupIdLabel;
    @FXML public TextField artifactIdField;
    @FXML public Label artifactIdLabel;
    @FXML public TextField packageField;
    @FXML public Label packageLabel;
    @FXML public ChoiceBox<String> javaVersionChoice;
    @FXML public Label javaVersionLabel;
    @FXML public ChoiceBox<String> packagingChoice;
    @FXML public Label packagingLabel;
    @FXML public Label nodePathLabel;
    @FXML public TextField nodePathField;
    @FXML public Button detectNodeButton;
    @FXML public Button installNodeButton;
    @FXML public Label nodeStatusLabel;
    @FXML public Label ngCliLabel;
    @FXML public TextField ngCliField;
    @FXML public Button detectNgButton;
    @FXML public Button installNgButton;
    @FXML public Label ngCliStatusLabel;
    @FXML public Label additionalParamsLabel;
    @FXML public TextField additionalParamsField;
    @FXML public VBox angularOptionsBox;
    @FXML public CheckBox angularStandaloneCheck;
    @FXML public CheckBox angularDefaultSetupCheck;
    @FXML public VBox angularSection;
    @FXML public VBox reactSection;
    @FXML public ChoiceBox<String> reactTypeChoice;
    @FXML public TextField reactCraField;
    @FXML public Button reactCraDetectButton;
    @FXML public CheckBox reactTypescriptCheck;
    @FXML public Label reactWarningLabel;
    @FXML public TextField locationField;
    @FXML public Label locationLabel;
    @FXML public Button browseButton;
    @FXML public Label targetPreviewLabel;
    @FXML public VBox springSection;
    @FXML public VBox nodeSection;
    @FXML public Label summaryTemplateLabel;
    @FXML public Label summaryNameLabel;
    @FXML public Label summaryLocationLabel;

    // Dependencies page
    @FXML public TextField depSearch;
    @FXML public ListView<CheckBox> dependencyList;
    @FXML public Label depInfoLabel;

    // Wizard buttons
    @FXML public Button backButton;
    @FXML public Button nextButton;
    @FXML public Button finishButton;
    @FXML public Button cancelButton;

    @FXML public RadioButton openNewWindow;
    @FXML public RadioButton openReplace;

    private ToggleGroup openModeGroup;

    private enum Template { SPRING, REACT, ANGULAR }
    private Template selectedTemplate = null;

    private final ObservableList<CheckBox> allDeps = FXCollections.observableArrayList();
    private FilteredList<CheckBox> filteredDeps;

    private int step = 0; // 0 template,1 details

    private static final String PREF_OPEN_MODE = "newProject.openMode";
    private static final String PREF_LAST_TEMPLATE = "newProject.lastTemplate";
    private static final String PREF_LAST_DIR = "newProject.lastDir";
    private final Preferences prefs = Preferences.userNodeForPackage(NewProjectWindowController.class);

    @FXML
    public void initialize() {
        // create ToggleGroup in code and wire radio buttons
        openModeGroup = new ToggleGroup();
        if (openNewWindow != null) openNewWindow.setToggleGroup(openModeGroup);
        if (openReplace != null) openReplace.setToggleGroup(openModeGroup);

        // Setup wizard navigation
        if (chooseSpringBtn != null) chooseSpringBtn.setOnAction(e -> { selectedTemplate = Template.SPRING; prefs.put(PREF_LAST_TEMPLATE, "SPRING"); showDetailsFor(Template.SPRING); });
        if (chooseReactBtn != null) chooseReactBtn.setOnAction(e -> { selectedTemplate = Template.REACT; prefs.put(PREF_LAST_TEMPLATE, "REACT"); showDetailsFor(Template.REACT); });
        if (chooseAngularBtn != null) chooseAngularBtn.setOnAction(e -> { selectedTemplate = Template.ANGULAR; prefs.put(PREF_LAST_TEMPLATE, "ANGULAR"); showDetailsFor(Template.ANGULAR); });

        if (backButton != null) backButton.setOnAction(e -> back());
        if (nextButton != null) nextButton.setOnAction(e -> next());
        if (finishButton != null) finishButton.setOnAction(e -> finish());
        if (cancelButton != null) cancelButton.setOnAction(e -> closeWindow());
        if (detectNodeButton != null) detectNodeButton.setOnAction(e -> autoDetectNodeAndCli(false, true));
        if (installNodeButton != null) installNodeButton.setOnAction(e -> showDownloadDialog(
                "Node.js",
                "Download Node.js from https://nodejs.org/en/download/ and install it.\n\n" +
                        "After installation, restart the IDE and click Auto-detect."
        ));
        if (detectNgButton != null) detectNgButton.setOnAction(e -> autoDetectNodeAndCli(true, true));
        if (installNgButton != null) installNgButton.setOnAction(e -> showDownloadDialog(
                "Angular CLI",
                "Install Angular CLI with:\n\n" +
                        "npm install -g @angular/cli\n\n" +
                        "Then restart the IDE and click Auto-detect."
        ));
        if (reactCraDetectButton != null) reactCraDetectButton.setOnAction(e -> autoDetectCreateReactApp());

        // Load last-used template and, if present, open its details page
        try {
            String lastTemplate = prefs.get(PREF_LAST_TEMPLATE, null);
            if (lastTemplate != null) {
                try {
                    Template t = Template.valueOf(lastTemplate);
                    selectedTemplate = t;
                    // open details for last template so user can continue quickly
                    showDetailsFor(t);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}

        // details defaults
        if (javaVersionChoice != null) {
            javaVersionChoice.getItems().addAll("8","11","17","21");
            javaVersionChoice.getSelectionModel().select("17");
        }
        if (packagingChoice != null) {
            packagingChoice.getItems().addAll("jar","war");
            packagingChoice.getSelectionModel().select("jar");
        }
        if (languageChoice != null) {
            languageChoice.getItems().addAll("Java", "Kotlin", "Groovy");
            languageChoice.getSelectionModel().select("Java");
        }
        if (typeChoice != null) {
            typeChoice.getItems().addAll("Maven", "Gradle - Groovy", "Gradle - Kotlin");
            typeChoice.getSelectionModel().select("Maven");
        }
        if (reactTypeChoice != null) {
            reactTypeChoice.getItems().addAll("React", "React Native", "Next.js");
            reactTypeChoice.getSelectionModel().select("React");
        }

        // location default from prefs
        try {
            String last = prefs.get(PREF_LAST_DIR, System.getProperty("user.dir"));
            if (locationField != null) locationField.setText(last);
        } catch (Exception ignored) {}

        // update preview when location or project name changes
        Runnable updatePreview = () -> {
            try {
                String loc = (locationField != null && !locationField.getText().isBlank()) ? locationField.getText() : prefs.get(PREF_LAST_DIR, System.getProperty("user.dir"));
                String name = (projectNameField != null && !projectNameField.getText().isBlank()) ? projectNameField.getText() : "my-project";
                Path preview = Paths.get(loc).resolve(name);
                if (targetPreviewLabel != null) targetPreviewLabel.setText("Target: " + preview.toAbsolutePath().toString());
                if (summaryNameLabel != null) summaryNameLabel.setText("Name: " + name);
                if (summaryLocationLabel != null) summaryLocationLabel.setText("Location: " + loc);
            } catch (Exception ignored) {}
        };
        if (locationField != null) locationField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        if (projectNameField != null) projectNameField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        updatePreview.run();

        if (browseButton != null) {
            browseButton.setOnAction(e -> {
                javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
                dc.setTitle("Select Project Location");
                java.io.File chosen = dc.showDialog(null);
                if (chosen != null && chosen.exists() && chosen.isDirectory()) {
                    if (locationField != null) {
                        locationField.setText(chosen.getAbsolutePath());
                        try { prefs.put(PREF_LAST_DIR, chosen.getAbsolutePath()); } catch (Exception ignored) {}
                    }
                }
            });
        }
        if (locationField != null) {
            locationField.textProperty().addListener((obs, oldV, newV) -> {
                try { if (newV != null && !newV.isBlank()) prefs.put(PREF_LAST_DIR, newV); } catch (Exception ignored) {}
            });
        }

        // dependency list wiring
        filteredDeps = new FilteredList<>(allDeps, p -> true);
        if (dependencyList != null) {
            dependencyList.setItems(allDeps);
            dependencyList.setCellFactory(lv -> new ListCell<>() { @Override protected void updateItem(CheckBox item, boolean empty) { super.updateItem(item, empty); if (empty||item==null) setGraphic(null); else setGraphic(item); } });
        }

        if (depSearch != null) depSearch.textProperty().addListener((obs, old, nw) -> {
            String q = (nw==null)?"":nw.toLowerCase().trim();
            filteredDeps.setPredicate(cb -> cb.getText().toLowerCase().contains(q));
            dependencyList.setItems(FXCollections.observableArrayList(filteredDeps));
        });

        // Load persisted open-mode preference and apply it (default: new window)
        try {
            String mode = prefs.get(PREF_OPEN_MODE, "new");
            if ("replace".equals(mode)) {
                if (openReplace != null) openReplace.setSelected(true);
            } else {
                if (openNewWindow != null) openNewWindow.setSelected(true);
            }
        } catch (Exception ignored) {}

        // Persist preference when selection changes
        if (openNewWindow != null) {
            openNewWindow.selectedProperty().addListener((obs, oldV, newV) -> {
                try { if (newV) prefs.put(PREF_OPEN_MODE, "new"); else prefs.put(PREF_OPEN_MODE, "replace"); } catch (Exception ignored) {}
            });
        }
        if (openReplace != null) {
            openReplace.selectedProperty().addListener((obs, oldV, newV) -> {
                try { if (newV) prefs.put(PREF_OPEN_MODE, "replace"); else prefs.put(PREF_OPEN_MODE, "new"); } catch (Exception ignored) {}
            });
        }

        // init page visibility
        setStep(1);
        if (backButton != null) { backButton.setVisible(false); backButton.setManaged(false); }
        if (nextButton != null) { nextButton.setVisible(false); nextButton.setManaged(false); }
        if (finishButton != null) { finishButton.setVisible(true); finishButton.setManaged(true); }

        autoDetectNodeAndCli(true, false);
    }

    private void setStep(int s) {
        step = s;
        if (templatePage != null) { templatePage.setVisible(step==0); templatePage.setManaged(step==0); }
        if (detailsPage != null) { detailsPage.setVisible(step==1); detailsPage.setManaged(step==1); }
        if (backButton != null) backButton.setDisable(step==0);
        if (nextButton != null) { nextButton.setVisible(step==0); nextButton.setManaged(step==0); }
        if (finishButton != null) { finishButton.setVisible(step==1); finishButton.setManaged(step==1); }
    }

    private void showDetailsFor(Template t) {
        // preset some defaults
        if (projectNameField==null) return;
        highlightTemplate(t);
        if (summaryTemplateLabel != null) summaryTemplateLabel.setText("Template: " + prettyTemplateName(t));
        switch (t) {
            case SPRING: {
                projectNameField.setText("demo");
                groupIdField.setText("com.example");
                artifactIdField.setText("demo");
                packageField.setText("com.example.demo");
                if (javaVersionChoice!=null) javaVersionChoice.getSelectionModel().select("17");
                if (packagingChoice!=null) packagingChoice.getSelectionModel().select("jar");
                if (springSection != null) { springSection.setVisible(true); springSection.setManaged(true); }
                if (nodeSection != null) { nodeSection.setVisible(false); nodeSection.setManaged(false); }
                if (angularSection != null) { angularSection.setVisible(false); angularSection.setManaged(false); }
                if (reactSection != null) { reactSection.setVisible(false); reactSection.setManaged(false); }
                setAngularExtrasVisible(false);
                populateDependenciesFor(t);
                break;
            }
            case REACT: {
                projectNameField.setText("react-app");
                if (springSection != null) { springSection.setVisible(false); springSection.setManaged(false); }
                if (nodeSection != null) { nodeSection.setVisible(true); nodeSection.setManaged(true); }
                if (angularSection != null) { angularSection.setVisible(false); angularSection.setManaged(false); }
                if (reactSection != null) { reactSection.setVisible(true); reactSection.setManaged(true); }
                setAngularExtrasVisible(false);
                autoDetectCreateReactApp();
                autoDetectNodeAndCli(false, true);
                break;
            }
            case ANGULAR: {
                projectNameField.setText("angular-app");
                if (springSection != null) { springSection.setVisible(false); springSection.setManaged(false); }
                if (nodeSection != null) { nodeSection.setVisible(true); nodeSection.setManaged(true); }
                if (angularSection != null) { angularSection.setVisible(true); angularSection.setManaged(true); }
                if (reactSection != null) { reactSection.setVisible(false); reactSection.setManaged(false); }
                setAngularExtrasVisible(true);
                autoDetectNodeAndCli(true, true);
                break;
            }
        }
    }

    private void back() { if (step>0) setStep(step-1); }
    private void next() {
        if (step == 0) {
            if (selectedTemplate == null) {
                Alert a = new Alert(Alert.AlertType.WARNING, "Select a template to continue.", ButtonType.OK);
                a.setTitle("Choose template");
                a.showAndWait();
                return;
            }
            setStep(1);
        }
    }

    private void highlightTemplate(Template t) {
        setTemplateSelected(chooseSpringBtn, t == Template.SPRING);
        setTemplateSelected(chooseReactBtn, t == Template.REACT);
        setTemplateSelected(chooseAngularBtn, t == Template.ANGULAR);
    }

    private void setTemplateSelected(Button button, boolean selected) {
        if (button == null) return;
        if (selected) {
            if (!button.getStyleClass().contains("wizard-nav-item-selected")) {
                button.getStyleClass().add("wizard-nav-item-selected");
            }
        } else {
            button.getStyleClass().remove("wizard-nav-item-selected");
        }
    }

    private void setAngularExtrasVisible(boolean visible) {
        if (additionalParamsLabel != null) { additionalParamsLabel.setVisible(visible); additionalParamsLabel.setManaged(visible); }
        if (additionalParamsField != null) { additionalParamsField.setVisible(visible); additionalParamsField.setManaged(visible); }
        if (angularOptionsBox != null) { angularOptionsBox.setVisible(visible); angularOptionsBox.setManaged(visible); }
    }

    private void autoDetectCreateReactApp() {
        if (reactCraField == null) return;
        String defaultCmd = "npx create-react-app";
        if (isBlank(reactCraField.getText())) {
            reactCraField.setText(defaultCmd);
        }
    }

    private String prettyTemplateName(Template t) {
        if (t == null) return "-";
        switch (t) {
            case SPRING: return "Spring Boot";
            case REACT: return "React";
            case ANGULAR: return "Angular";
            default: return t.name();
        }
    }

    private void autoDetectNodeAndCli(boolean includeNg, boolean allowFieldUpdate) {
        Thread t = new Thread(() -> {
            String nodePath = resolveExecutable("node");
            String nodeVersion = (nodePath != null) ? readVersion(nodePath, "--version") : null;
            String ngPath = includeNg ? resolveExecutable("ng") : null;
            String ngVersion = (ngPath != null) ? readVersion(ngPath, "--version") : null;

            Platform.runLater(() -> {
                if (allowFieldUpdate && nodePathField != null && isBlank(nodePathField.getText()) && nodePath != null) {
                    nodePathField.setText(nodePath);
                }
                if (nodeStatusLabel != null) {
                    nodeStatusLabel.setText(nodePath != null
                            ? "Node: " + (nodeVersion != null ? nodeVersion : "detected")
                            : "Node: not detected");
                }
                if (includeNg) {
                    if (allowFieldUpdate && ngCliField != null && isBlank(ngCliField.getText()) && ngPath != null) {
                        ngCliField.setText(ngPath);
                    }
                    if (ngCliStatusLabel != null) {
                        ngCliStatusLabel.setText(ngPath != null
                                ? "Angular CLI: " + (ngVersion != null ? ngVersion : "detected")
                                : "Angular CLI: not detected");
                    }
                }
            });
        }, "node-detector");
        t.setDaemon(true);
        t.start();
    }

    private String resolveExecutable(String command) {
        String explicit = findInPath(command);
        if (explicit != null) return explicit;
        return null;
    }

    private String findInPath(String command) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return null;
        String[] dirs = path.split(java.io.File.pathSeparator);
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String[] exts = isWindows ? new String[] {".exe", ".cmd", ".bat"} : new String[] {""};
        for (String dir : dirs) {
            if (dir == null || dir.isBlank()) continue;
            for (String ext : exts) {
                Path p = Paths.get(dir, command + ext);
                if (Files.exists(p) && Files.isRegularFile(p) && Files.isExecutable(p)) {
                    return p.toAbsolutePath().toString();
                }
            }
        }
        return null;
    }

    private String readVersion(String command, String arg) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, arg);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                p.waitFor(2, TimeUnit.SECONDS);
                if (line != null && !line.isBlank()) return line.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private void showDownloadDialog(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setTitle(title);
        a.initModality(Modality.APPLICATION_MODAL);
        a.showAndWait();
    }

    private void populateDependenciesFor(Template t) {
        allDeps.clear();
        if (t==Template.SPRING) {
            // scan local m2 for common spring starters
            Path m2 = Paths.get(System.getProperty("user.home"), ".m2", "repository");
            List<String> candidates = Arrays.asList("org/springframework/boot/spring-boot-starter-web", "org/springframework/boot/spring-boot-starter-data-jpa", "org/springframework/boot/spring-boot-starter-security", "org/projectlombok/lombok");
            for (String can : candidates) {
                Path p = m2.resolve(can);
                if (Files.exists(p)) {
                    String id = guessArtifactIdFromPath(can);
                    CheckBox cb = new CheckBox(id);
                    allDeps.add(cb);
                }
            }
            // fallback defaults if none found
            if (allDeps.isEmpty()) {
                allDeps.add(new CheckBox("spring-boot-starter-web"));
                allDeps.add(new CheckBox("spring-boot-starter-data-jpa"));
                allDeps.add(new CheckBox("spring-boot-starter-security"));
                allDeps.add(new CheckBox("lombok"));
            }
        } else if (t==Template.REACT) {
            // show npm packages placeholder
            allDeps.add(new CheckBox("react")); allDeps.add(new CheckBox("react-dom"));
        } else if (t==Template.ANGULAR) {
            allDeps.add(new CheckBox("@angular/core")); allDeps.add(new CheckBox("@angular/cli"));
        }
        filteredDeps = new FilteredList<>(allDeps, p->true);
        if (dependencyList!=null) dependencyList.setItems(FXCollections.observableArrayList(filteredDeps));
    }

    private String guessArtifactIdFromPath(String repoPath) {
        String[] parts = repoPath.split("/");
        return parts[parts.length-1];
    }

    private void finish() {
        // read common fields and call generator accordingly
        String name = projectNameField.getText();
        String group = groupIdField.getText();
        String artifact = artifactIdField.getText();
        String pkg = packageField.getText();
        String javaVer = (javaVersionChoice!=null)?javaVersionChoice.getValue():"17";
        String packaging = (packagingChoice!=null)?packagingChoice.getValue():"jar";
        if (name==null || name.isBlank()) name = "my-project";
        if (group==null || group.isBlank()) group = "com.example";
        if (artifact==null || artifact.isBlank()) artifact = name;
        if (pkg==null || pkg.isBlank()) pkg = group + "." + artifact;

        // prefer last-used directory as base
        Path baseDir;
        try {
            String loc = (locationField != null && !locationField.getText().isBlank()) ? locationField.getText() : prefs.get(PREF_LAST_DIR, System.getProperty("user.dir"));
            baseDir = Paths.get(loc);
        } catch (Exception ex) {
            baseDir = Paths.get(System.getProperty("user.dir"));
        }
        Path target = baseDir.resolve(name);
        // validate parent directory exists and is writable; if missing ask to create it
        try {
            Path parent = target.getParent();
            if (parent == null) {
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR, "Invalid target path: " + target.toString(), ButtonType.OK);
                    a.setTitle("Invalid location"); a.showAndWait();
                });
                return;
            }

            if (!Files.exists(parent)) {
                // Ask user to create the parent directory
                boolean create = confirm("Create directory", "The parent directory '" + parent.toAbsolutePath().toString() + "' does not exist. Create it?");
                if (create) {
                    try {
                        Files.createDirectories(parent);
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            Alert a = new Alert(Alert.AlertType.ERROR, "Failed to create directory: " + ex.getMessage(), ButtonType.OK);
                            a.setTitle("Create failed"); a.showAndWait();
                        });
                        return;
                    }
                } else {
                    return;
                }
            }

            if (!Files.isWritable(parent)) {
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR, "The selected location is not writable: " + parent.toAbsolutePath().toString(), ButtonType.OK);
                    a.setTitle("Invalid location"); a.showAndWait();
                });
                return;
            }

        } catch (Exception ex) {
            // ignore and proceed
        }

        // If target already exists, ask user whether to overwrite
        if (Files.exists(target)) {
            boolean ok = confirm("Target exists", "The target folder '" + target.toAbsolutePath().toString() + "' already exists. Overwrite its contents?");
            if (!ok) return;
            // delete existing tree under target
            try (java.util.stream.Stream<Path> s = Files.walk(target)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR, "Failed to clear existing directory: " + ex.getMessage(), ButtonType.OK);
                    a.setTitle("Error"); a.showAndWait();
                });
                return;
            }
        }

        try {
             boolean web = isDepSelected("spring-boot-starter-web");
             boolean data = isDepSelected("spring-boot-starter-data-jpa");
             boolean sec = isDepSelected("spring-boot-starter-security");
             boolean lombok = isDepSelected("lombok");
             ProjectGenerator gen = new ProjectGenerator();
             if (selectedTemplate==Template.SPRING) {
                 gen.generateSpringBootProject(target, group, artifact, pkg, "0.0.1-SNAPSHOT", packaging, javaVer, web, data, sec, lombok);
             } else if (selectedTemplate==Template.REACT) {
                 gen.generateAndCollect("React", target, false, false, false, false);
             } else if (selectedTemplate==Template.ANGULAR) {
                 gen.generateAndCollect("Angular", target, false, false, false, false);
             }

             if (ServiceManager.isInitialized() && ServiceManager.getIndexer()!=null) {
                 // simple indexing of created project files
                 Files.walk(target).filter(Files::isRegularFile).forEach(p -> ServiceManager.getIndexer().indexFileAsync(p));
             }

            // If this is a Spring Boot project, offer to run 'mvn -DskipTests package' to fetch deps and build
            if (selectedTemplate == Template.SPRING && isCommandAvailable("mvn")) {
                boolean runMvn = confirm("Build project", "Run 'mvn -DskipTests package' now to download dependencies and build the project?");
                if (runMvn) {
                    // run mvn package inside the created project folder
                    runCommandWithOutput(new String[]{"mvn","-DskipTests=true","package"}, target);
                }
            }

            // persist last project directory (parent of created project)
            try {
                Path parent = target.getParent();
                if (parent != null) prefs.put(PREF_LAST_DIR, parent.toAbsolutePath().toString());
            } catch (Exception ignored) {}

             // final UI action: open/replace workspace
             boolean openNew = true;
             if (openNewWindow!=null && openReplace!=null) {
                 openNew = openNewWindow.isSelected();
             } else {
                 // fall back to preference
                 openNew = "new".equals(prefs.get(PREF_OPEN_MODE, "new"));
             }

             if (openNew) {
                 // Try runnable jar first (if packaged) and verify startup
                 File jar = new File("target/mazeidea-1.0-SNAPSHOT.jar");
                 boolean launched = false;
                 if (jar.exists()) {
                     List<String> cmd = Arrays.asList("java", "-jar", jar.getAbsolutePath(), target.toAbsolutePath().toString());
                     Process p = tryLaunchAndVerify(cmd, null, 8);
                     launched = (p != null);
                 }

                 // Next try to run via mvnw (preferred) or mvn from the IDE project root
                 if (!launched) {
                     try {
                         Path ideRoot = Paths.get(System.getProperty("user.dir"));
                         String mvncmd = null;
                         if (new File(ideRoot.toFile(), "mvnw").exists()) mvncmd = "./mvnw";
                         else if (isCommandAvailable("mvn")) mvncmd = "mvn";
                         if (mvncmd != null) {
                             List<String> cmd = Arrays.asList(mvncmd, "javafx:run", "-Dexec.args=" + target.toAbsolutePath().toString());
                             Process p = tryLaunchAndVerify(cmd, ideRoot, 10);
                             launched = (p != null);
                         }
                     } catch (Exception ignored) {}
                 }

                 // Last fallback: launch using current classpath + Launcher main
                 if (!launched) {
                     try {
                         String cp = System.getProperty("java.class.path");
                         if (cp != null && !cp.isBlank()) {
                             List<String> cmd = new ArrayList<>();
                             cmd.add("java"); cmd.add("-cp"); cmd.add(cp); cmd.add("com.maze.mazeidea.Launcher"); cmd.add(target.toAbsolutePath().toString());
                             Process p = tryLaunchAndVerify(cmd, null, 8);
                             launched = (p != null);
                         }
                     } catch (Exception ex) { launched = false; }
                 }

                 if (!launched) {
                     // If verification failed, open in current window instead
                     final Path finalTarget = target;
                     Platform.runLater(() -> {
                         Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                                 "Couldn't reliably start a new IDE window. Open the created project in the current window instead?",
                                 ButtonType.YES, ButtonType.NO);
                         a.setTitle("Open project");
                         a.showAndWait().ifPresent(btn -> {
                             if (btn == ButtonType.YES) {
                                 try {
                                     WorkspaceManager.setWorkspace(finalTarget);
                                     ServiceManager.switchWorkspace(finalTarget);
                                 } catch (Exception ignored) {}
                             } else {
                                 Alert info = new Alert(Alert.AlertType.INFORMATION, "Created project at: " + finalTarget.toAbsolutePath().toString(), ButtonType.OK);
                                 info.setTitle("Project created"); info.showAndWait();
                             }
                         });
                     });
                 }
             } else {
                 WorkspaceManager.setWorkspace(target);
                 ServiceManager.switchWorkspace(target);
             }

         } catch (Exception e) {
             e.printStackTrace();
         }
     }

    private boolean isDepSelected(String id) {
        for (CheckBox cb : allDeps) if (cb.getText().equals(id) && cb.isSelected()) return true;
        return false;
    }

    private void closeWindow() {
        try {
            Stage stage = (Stage) projectNameField.getScene().getWindow();
            stage.close();
        } catch (Exception ignored) {}
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getOutputStream().close();
            int rc = p.waitFor();
            return rc == 0 || rc == 1;
        } catch (Exception e) { return false; }
    }

    private boolean confirm(String title, String content) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.YES, ButtonType.NO);
        a.setTitle(title);
        a.initModality(Modality.APPLICATION_MODAL);
        a.showAndWait();
        return a.getResult() == ButtonType.YES;
    }

    private void runCommandWithOutput(String[] command, Path workingDir) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Running: " + String.join(" ", command));
        TextArea out = new TextArea(); out.setEditable(false); out.setWrapText(true);
        Scene scene = new Scene(out, 600, 400);
        stage.setScene(scene); stage.show();
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                if (workingDir != null) pb.directory(workingDir.toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String l = line + System.lineSeparator();
                        Platform.runLater(() -> out.appendText(l));
                    }
                }
                int exit = p.waitFor();
                Platform.runLater(() -> out.appendText("\nProcess exited with code: " + exit));
            } catch (Exception ex) {
                Platform.runLater(() -> out.appendText("\nError running command: " + ex.getMessage()));
            }
        }, "project-installer").start();
    }

    /**
     * Start a process, stream its output to a dialog, and verify the launch succeeded by ensuring
     * the process is still alive after the given timeout (in seconds). If the process exits quickly
     * (before the timeout) we treat the launch as failed.
     * Returns the Process if started (may be alive) or null on immediate failure.
     */
    private Process tryLaunchAndVerify(List<String> cmd, Path workingDir, int verifySeconds) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Launching: " + String.join(" ", cmd));
        TextArea out = new TextArea(); out.setEditable(false); out.setWrapText(true);
        Scene scene = new Scene(out, 700, 420);
        stage.setScene(scene);
        stage.show();

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (workingDir != null) pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // stream output
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String l = line + System.lineSeparator();
                        Platform.runLater(() -> out.appendText(l));
                    }
                } catch (IOException ignored) {}
            }, "launcher-output-reader");
            reader.setDaemon(true);
            reader.start();

            // verify: wait verifySeconds; if process exits before that -> failure
            boolean exited = p.waitFor(verifySeconds, TimeUnit.SECONDS);
            if (exited) {
                // read any remaining output
                try { int rc = p.exitValue(); Platform.runLater(() -> out.appendText("\nProcess exited with code: " + rc)); } catch (Exception ignored) {}
                try { p.destroyForcibly(); } catch (Exception ignored) {}
                return null;
            }

            // process still alive after timeout -> assume successful startup
            return p;
        } catch (Exception e) {
            Platform.runLater(() -> {
                out.appendText("\nFailed to launch: " + e.getMessage());
            });
            return null;
        }
    }

    public void open() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("new-project.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("New Project");
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) { e.printStackTrace(); }
    }
}
