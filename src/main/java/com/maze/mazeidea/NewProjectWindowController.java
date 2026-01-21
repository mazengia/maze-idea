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
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class NewProjectWindowController {
    // Wizard pages
    @FXML public StackPane wizardStack;
    @FXML public Node templatePage;
    @FXML public Node detailsPage;
    @FXML public Node depsPage;

    // Template page controls
    @FXML public Button chooseSpringBtn;
    @FXML public Button chooseReactBtn;
    @FXML public Button chooseAngularBtn;

    // Details page
    @FXML public TextField projectNameField;
    @FXML public TextField groupIdField;
    @FXML public TextField artifactIdField;
    @FXML public TextField packageField;
    @FXML public ChoiceBox<String> javaVersionChoice;
    @FXML public ChoiceBox<String> packagingChoice;
    @FXML public Label nodePathLabel;
    @FXML public TextField nodePathField;
    @FXML public Label ngCliLabel;
    @FXML public TextField ngCliField;

    // Dependencies page
    @FXML public TextField depSearch;
    @FXML public ListView<CheckBox> dependencyList;
    @FXML public Label depInfoLabel;

    // Wizard buttons
    @FXML public Button backButton;
    @FXML public Button nextButton;
    @FXML public Button finishButton;

    @FXML public RadioButton openNewWindow;
    @FXML public RadioButton openReplace;

    private ToggleGroup openModeGroup;

    private enum Template { SPRING, REACT, ANGULAR }
    private Template selectedTemplate = null;

    private final ObservableList<CheckBox> allDeps = FXCollections.observableArrayList();
    private FilteredList<CheckBox> filteredDeps;

    private int step = 0; // 0 template,1 details,2 deps

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
        setStep(0);

        // Back/Next initial state
        if (backButton != null) backButton.setDisable(true);
        if (finishButton != null) { finishButton.setVisible(false); finishButton.setManaged(false); }
    }

    private void setStep(int s) {
        step = s;
        if (templatePage != null) { templatePage.setVisible(step==0); templatePage.setManaged(step==0); }
        if (detailsPage != null) { detailsPage.setVisible(step==1); detailsPage.setManaged(step==1); }
        if (depsPage != null) { depsPage.setVisible(step==2); depsPage.setManaged(step==2); }
        if (backButton != null) backButton.setDisable(step==0);
        if (nextButton != null) nextButton.setDisable(step==2);
        if (finishButton != null) { finishButton.setVisible(step==2); finishButton.setManaged(step==2); }
    }

    private void showDetailsFor(Template t) {
        // preset some defaults
        if (projectNameField==null) return;
        switch (t) {
            case SPRING: {
                projectNameField.setText("demo");
                groupIdField.setText("com.example");
                artifactIdField.setText("demo");
                packageField.setText("com.example.demo");
                if (javaVersionChoice!=null) javaVersionChoice.getSelectionModel().select("17");
                if (packagingChoice!=null) packagingChoice.getSelectionModel().select("jar");
                if (nodePathLabel!=null) { nodePathLabel.setVisible(false); nodePathLabel.setManaged(false); }
                if (nodePathField!=null) { nodePathField.setVisible(false); nodePathField.setManaged(false); }
                if (ngCliLabel!=null) { ngCliLabel.setVisible(false); ngCliLabel.setManaged(false); }
                if (ngCliField!=null) { ngCliField.setVisible(false); ngCliField.setManaged(false); }
                break;
            }
            case REACT: {
                projectNameField.setText("react-app");
                if (groupIdField!=null) groupIdField.setText(""); if (artifactIdField!=null) artifactIdField.setText(""); if (packageField!=null) packageField.setText("");
                if (nodePathLabel!=null) { nodePathLabel.setVisible(true); nodePathLabel.setManaged(true); }
                if (nodePathField!=null) { nodePathField.setVisible(true); nodePathField.setManaged(true); }
                if (ngCliLabel!=null) { ngCliLabel.setVisible(false); ngCliLabel.setManaged(false); }
                if (ngCliField!=null) { ngCliField.setVisible(false); ngCliField.setManaged(false); }
                break;
            }
            case ANGULAR: {
                projectNameField.setText("angular-app");
                if (groupIdField!=null) groupIdField.setText(""); if (artifactIdField!=null) artifactIdField.setText(""); if (packageField!=null) packageField.setText("");
                if (nodePathLabel!=null) { nodePathLabel.setVisible(true); nodePathLabel.setManaged(true); }
                if (nodePathField!=null) { nodePathField.setVisible(true); nodePathField.setManaged(true); }
                if (ngCliLabel!=null) { ngCliLabel.setVisible(true); ngCliLabel.setManaged(true); }
                if (ngCliField!=null) { ngCliField.setVisible(true); ngCliField.setManaged(true); }
                break;
            }
        }
        setStep(1);
    }

    private void back() { if (step>0) setStep(step-1); }
    private void next() {
        if (step==0) { /* shouldn't happen */ }
        else if (step==1) {
            // prepare deps page: detect local maven artifacts (for spring) or npm (for node)
            populateDependenciesFor(selectedTemplate);
            setStep(2);
        }
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
        Path baseDir = Paths.get(prefs.get(PREF_LAST_DIR, System.getProperty("user.dir")));
        Path target = baseDir.resolve(name);
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
                 File jar = new File("target/mazeidea-1.0-SNAPSHOT.jar");
                 if (jar.exists()) {
                     new ProcessBuilder("java","-jar",jar.getAbsolutePath(), target.toAbsolutePath().toString()).start();
                 } else if (isCommandAvailable("mvn")) {
                     // prefer using maven to run the app from classes/resources
                     runCommandWithOutput(new String[]{"mvn","-DskipTests=false","javafx:run","-Dexec.args=\""+target.toAbsolutePath().toString()+"\""}, target.getParent());
                 } else {
                     // fallback: try to start a new JVM using same classpath if provided via system property
                     String cp = System.getProperty("java.class.path");
                     boolean launched = false;
                     if (cp != null && !cp.isBlank()) {
                         try {
                             List<String> cmd = new ArrayList<>();
                             cmd.add("java"); cmd.add("-cp"); cmd.add(cp); cmd.add("com.maze.mazeidea.Launcher"); cmd.add(target.toAbsolutePath().toString());
                             new ProcessBuilder(cmd).inheritIO().start();
                             launched = true;
                         } catch (Exception ex) {
                             launched = false;
                         }
                     }

                     if (!launched) {
                         // Ask user whether to open in current window instead
                         final Path finalTarget = target;
                         Platform.runLater(() -> {
                             Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                                     "Can't open a new IDE window automatically because no launcher was found.\nWould you like to open the created project in the current window instead?",
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

    private boolean isCommandAvailable(String cmd) { try { ProcessBuilder pb=new ProcessBuilder(cmd,"--version"); pb.redirectErrorStream(true); Process p=pb.start(); p.getOutputStream().close(); int rc=p.waitFor(); return rc==0||rc==1; } catch (Exception e){return false;} }

    private boolean confirm(String title, String content) { Alert a=new Alert(Alert.AlertType.CONFIRMATION,content,ButtonType.YES,ButtonType.NO); a.setTitle(title); a.initModality(Modality.APPLICATION_MODAL); a.showAndWait(); return a.getResult()==ButtonType.YES; }

    private void runCommandWithOutput(String[] command, Path workingDir) {
        Stage stage=new Stage(); stage.initModality(Modality.APPLICATION_MODAL); stage.setTitle("Running: "+String.join(" ",command)); TextArea out=new TextArea(); out.setEditable(false); out.setWrapText(true); Scene scene=new Scene(out,600,400); stage.setScene(scene); stage.show(); new Thread(()->{ try{ ProcessBuilder pb=new ProcessBuilder(command); pb.directory(workingDir.toFile()); pb.redirectErrorStream(true); Process p=pb.start(); try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){ String line; while((line=r.readLine())!=null){ String l=line+System.lineSeparator(); Platform.runLater(()->out.appendText(l)); } } int exit=p.waitFor(); Platform.runLater(()->out.appendText("\nProcess exited with code: "+exit)); }catch(Exception ex){ Platform.runLater(()->out.appendText("\nError running command: "+ex.getMessage())); }},"project-installer").start(); }

    public void open() { try { FXMLLoader loader=new FXMLLoader(getClass().getResource("new-project.fxml")); Scene scene=new Scene(loader.load()); Stage stage=new Stage(); stage.setTitle("New Project"); stage.setScene(scene); stage.show(); } catch (IOException e) { e.printStackTrace(); } }
}
