package com.maze.mazeidea.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProjectModel {
    private final Path root;
    private final List<Path> sourceRoots = new ArrayList<>();
    private final List<String> modules = new ArrayList<>();
    private final List<String> dependencies = new ArrayList<>();

    public ProjectModel(Path root) {
        this.root = root;
    }

    public Path getRoot() { return root; }
    public List<Path> getSourceRoots() { return sourceRoots; }
    public List<String> getModules() { return modules; }
    public List<String> getDependencies() { return dependencies; }

    public void addSourceRoot(Path p) { sourceRoots.add(p); }
    public void addModule(String m) { modules.add(m); }
    public void addDependency(String d) { dependencies.add(d); }
}
