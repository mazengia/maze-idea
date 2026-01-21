package com.maze.mazeidea.fs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileWatcherServiceTest {
    @Test
    public void testCreateEvent() throws Exception {
        Path tempDir = Files.createTempDirectory("fwtest");
        FileWatcherService watcher = new FileWatcherService(tempDir);
        List<FileEvent> events = new ArrayList<>();
        watcher.registerListener(events::add);
        watcher.start();

        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");

        // wait up to 3 seconds for event
        for (int i = 0; i < 30 && events.isEmpty(); i++) Thread.sleep(100);

        watcher.stop();
        assertTrue(events.stream().anyMatch(e -> e.path().equals(file) && e.type() == FileEvent.EventType.CREATE));
    }
}
