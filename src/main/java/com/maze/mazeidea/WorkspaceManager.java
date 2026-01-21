package com.maze.mazeidea;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class WorkspaceManager {
    private static volatile Path workspaceRoot;
    private static final List<Consumer<Path>> listeners = new CopyOnWriteArrayList<>();

    private WorkspaceManager() {}

    public static void setWorkspace(Path root) {
        workspaceRoot = root;
        for (Consumer<Path> l : listeners) {
            try { l.accept(root); } catch (Exception ignored) {}
        }
    }

    public static Path getWorkspaceRoot() { return workspaceRoot; }

    public static void addListener(Consumer<Path> listener) { listeners.add(listener); }
    public static void removeListener(Consumer<Path> listener) { listeners.remove(listener); }
}
