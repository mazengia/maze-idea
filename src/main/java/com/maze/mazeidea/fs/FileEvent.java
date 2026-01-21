package com.maze.mazeidea.fs;

import java.nio.file.Path;

public class FileEvent {
    private final Path path;
    private final EventType type;
    private final long timestamp;

    public FileEvent(Path path, EventType type, long timestamp) {
        this.path = path;
        this.type = type;
        this.timestamp = timestamp;
    }

    public Path path() { return path; }
    public EventType type() { return type; }
    public long timestamp() { return timestamp; }

    public enum EventType { CREATE, MODIFY, DELETE }
}
