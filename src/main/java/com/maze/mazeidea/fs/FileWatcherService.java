package com.maze.mazeidea.fs;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FileWatcherService {
    private final Path root;
    private WatchService watchService;
    private final List<Consumer<FileEvent>> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "file-watcher"));
    private volatile boolean running = false;
    private final CountDownLatch readyLatch = new CountDownLatch(1);

    public FileWatcherService(Path root) {
        this.root = root;
    }

    public Path getRoot() { return root; }

    public void registerListener(Consumer<FileEvent> listener) {
        listeners.add(listener);
    }

    public void start() {
        running = true;
        executor.submit(this::runLoop);
        try {
            // wait up to 5 seconds for watcher to be ready and registered
            readyLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() throws IOException {
        running = false;
        if (watchService != null) watchService.close();
        executor.shutdownNow();
    }

    private void runLoop() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            this.watchService = ws;
            // register root and its subdirectories (one level deep for simplicity)
            try {
                Files.walk(root)
                        .filter(Files::isDirectory)
                        .forEach(dir -> {
                            try {
                                dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                            } catch (IOException e) {
                                // ignore registration failures for now
                            }
                        });
            } catch (IOException e) {
                // ignore walk failures
            } finally {
                readyLatch.countDown();
            }

            while (running) {
                WatchKey key;
                try {
                    key = ws.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                for (WatchEvent<?> ev : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = ev.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Path dir = (Path) key.watchable();
                    Path relative = (Path) ev.context();
                    Path full = dir.resolve(relative);

                    FileEvent.EventType type;
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) type = FileEvent.EventType.CREATE;
                    else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) type = FileEvent.EventType.MODIFY;
                    else type = FileEvent.EventType.DELETE;

                    FileEvent fe = new FileEvent(full, type, System.currentTimeMillis());
                    for (Consumer<FileEvent> l : listeners) {
                        try { l.accept(fe); } catch (Exception ignore) {}
                    }
                }

                boolean valid = key.reset();
                if (!valid) break;
            }
        } catch (IOException e) {
            // log and exit
            readyLatch.countDown();
        }
    }
}
