package com.maze.mazeidea.fs;

import java.nio.file.Path;

public record FileEvent(Path path, EventType type, long timestamp) {
    public enum EventType { CREATE, MODIFY, DELETE }
}
