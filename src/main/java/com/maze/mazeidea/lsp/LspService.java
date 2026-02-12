package com.maze.mazeidea.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LSP service with basic LSP4J integration. Starts external LSP processes per workspace (when configured),
 * connects via JSON-RPC, initializes the server, and forwards textDocument/didOpen/didChange requests.
 * Also exposes completion requests.
 */
public class LspService {
    private final Map<Path, Process> workspaceProcesses = new ConcurrentHashMap<>();
    private final Map<Path, LanguageServer> workspaceServers = new ConcurrentHashMap<>();
    private final Map<String, Integer> docVersions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Set<String>> fileSymbols = new ConcurrentHashMap<>();
    private final Map<Path, ConcurrentHashMap<String, Integer>> workspaceSymbolCounts = new ConcurrentHashMap<>();

    private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final String[] FALLBACK_KEYWORDS = new String[]{"public","private","protected","class","void","int","String","new","return","if","else","for","while","switch","case"};

    public LspService() {}

    public void startForWorkspace(Path workspace) {
        if (workspaceServers.containsKey(workspace)) return;
        String cmdTemplate = System.getProperty("mazeidea.lsp.command");
        if (cmdTemplate == null || cmdTemplate.isBlank()) return;
        String cmdStr = cmdTemplate.replace("${workspace}", workspace.toAbsolutePath().toString());
        try {
            String[] parts = cmdStr.split(" ");
            ProcessBuilder pb = new ProcessBuilder(parts);
            pb.directory(workspace.toFile());
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process p = pb.start();
            workspaceProcesses.put(workspace, p);

            InputStream in = p.getInputStream();
            OutputStream out = p.getOutputStream();

            LanguageClientImpl client = new LanguageClientImpl(workspace);
            Launcher<LanguageServer> launcher = Launcher.createLauncher(client, LanguageServer.class, in, out, executor, null);
            Future<?> start = launcher.startListening();
            LanguageServer server = launcher.getRemoteProxy();
            workspaceServers.put(workspace, server);

            // initialize
            InitializeParams init = new InitializeParams();
            init.setRootUri(workspace.toUri().toString());
            init.setCapabilities(new org.eclipse.lsp4j.ClientCapabilities());
            CompletableFuture<InitializeResult> res = server.initialize(init);
            res.orTimeout(5, TimeUnit.SECONDS).whenComplete((r, ex) -> {
                if (ex != null) System.err.println("LSP init failed: " + ex.getMessage());
                else {
                    try {
                        server.initialized(new InitializedParams());
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to start LSP for workspace: " + e.getMessage());
        }
    }

    public void stopForWorkspace(Path workspace) {
        LanguageServer server = workspaceServers.remove(workspace);
        if (server != null) {
            try {
                server.shutdown().get(2, TimeUnit.SECONDS);
                server.exit();
            } catch (Exception ignored) {}
        }
        Process p = workspaceProcesses.remove(workspace);
        if (p != null) p.destroy();
        workspaceSymbolCounts.remove(workspace);
    }

    public void shutdown() {
        for (Path w : workspaceServers.keySet()) stopForWorkspace(w);
        for (Process p : workspaceProcesses.values()) { try { p.destroy(); } catch (Exception ignored) {} }
        workspaceProcesses.clear();
        workspaceServers.clear();
        executor.shutdownNow();
    }

    public void didOpen(Path file, String text) {
        Path workspace = workspaceForFile(file);
        LanguageServer server = workspace != null ? workspaceServers.get(workspace) : null;
        updateSymbols(workspace, file, text);
        if (server == null) return;
        TextDocumentItem item = new TextDocumentItem(file.toUri().toString(), languageIdFor(file), 1, text);
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(item);
        server.getTextDocumentService().didOpen(params);
        docVersions.put(file.toUri().toString(), 1);
    }

    public void didChange(Path file, String newText) {
        Path workspace = workspaceForFile(file);
        LanguageServer server = workspace != null ? workspaceServers.get(workspace) : null;
        updateSymbols(workspace, file, newText);
        if (server == null) return;
        String uri = file.toUri().toString();
        int ver = docVersions.getOrDefault(uri, 1) + 1;
        docVersions.put(uri, ver);
        VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier(uri, ver);
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent(newText);
        DidChangeTextDocumentParams params = new DidChangeTextDocumentParams(id, Arrays.asList(change));
        server.getTextDocumentService().didChange(params);
    }

    public String[] complete(Path file, int offset, String prefix) {
        String text = null;
        try { text = Files.readString(file); } catch (Exception ignored) {}
        return complete(file, offset, prefix, text);
    }

    public String[] complete(Path file, int offset, String prefix, String text) {
        Path workspace = workspaceForFile(file);
        LanguageServer server = workspace != null ? workspaceServers.get(workspace) : null;
        if (server == null) {
            return fallbackCompletions(workspace, prefix);
        }
        try {
            String uri = file.toUri().toString();
            String content = text != null ? text : Files.readString(file);
            Position pos = positionFromOffset(content, offset);
            TextDocumentIdentifier tid = new TextDocumentIdentifier(uri);
            CompletionParams cp = new CompletionParams(tid, pos);
            CompletableFuture<Either<List<CompletionItem>, CompletionList>> fut = server.getTextDocumentService().completion(cp);
            Either<List<CompletionItem>, CompletionList> res = fut.get(2, TimeUnit.SECONDS);
            List<CompletionItem> items = res.isLeft() ? res.getLeft() : res.getRight().getItems();
            String[] lspItems = items.stream().map(CompletionItem::getLabel).filter(s -> prefix == null || s.startsWith(prefix)).toArray(String[]::new);
            return mergeCompletions(lspItems, fallbackCompletions(workspace, prefix));
        } catch (Exception e) {
            System.err.println("Completion error: " + e.getMessage());
            return fallbackCompletions(workspace, prefix);
        }
    }

    private Position positionFromOffset(String text, int offset) {
        int line = 0, col = 0, i = 0;
        while (i < offset && i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n') { line++; col = 0; }
            else col++;
            i++;
        }
        return new Position(line, col);
    }

    private String languageIdFor(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".ts")) return "typescript";
        if (name.endsWith(".js")) return "javascript";
        return "plaintext";
    }

    private Path workspaceForFile(Path file) {
        Path best = null;
        for (Path root : workspaceServers.keySet()) {
            if (file.startsWith(root)) {
                if (best == null || root.getNameCount() > best.getNameCount()) best = root;
            }
        }
        if (best != null) return best;
        try {
            Path root = com.maze.mazeidea.WorkspaceManager.getWorkspaceRoot();
            if (root != null && file.startsWith(root)) return root;
        } catch (Exception ignored) {}
        return file.getParent();
    }

    private void updateSymbols(Path workspace, Path file, String text) {
        if (workspace == null || text == null) return;
        Set<String> next = extractSymbols(text);
        String uri = file.toUri().toString();
        Set<String> prev = fileSymbols.put(uri, next);
        ConcurrentHashMap<String, Integer> counts = workspaceSymbolCounts.computeIfAbsent(workspace, k -> new ConcurrentHashMap<>());
        if (prev != null) {
            for (String sym : prev) {
                counts.computeIfPresent(sym, (k, v) -> v > 1 ? v - 1 : null);
            }
        }
        for (String sym : next) {
            counts.merge(sym, 1, Integer::sum);
        }
    }

    private Set<String> extractSymbols(String text) {
        Set<String> out = new HashSet<>();
        Matcher m = SYMBOL_PATTERN.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private String[] fallbackCompletions(Path workspace, String prefix) {
        Set<String> out = new HashSet<>();
        for (String k : FALLBACK_KEYWORDS) {
            if (prefix == null || k.startsWith(prefix)) out.add(k);
        }
        if (workspace != null) {
            Map<String, Integer> counts = workspaceSymbolCounts.get(workspace);
            if (counts != null) {
                for (String sym : counts.keySet()) {
                    if (prefix == null || sym.startsWith(prefix)) out.add(sym);
                }
            }
        }
        return out.toArray(new String[0]);
    }

    private String[] mergeCompletions(String[] primary, String[] fallback) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (primary != null) out.addAll(Arrays.asList(primary));
        if (fallback != null) out.addAll(Arrays.asList(fallback));
        return out.toArray(new String[0]);
    }

    private static class LanguageClientImpl implements LanguageClient {
        private final Path workspace;
        public LanguageClientImpl(Path workspace) { this.workspace = workspace; }

        @Override public void telemetryEvent(Object object) {}
        @Override public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            System.err.println("Diagnostics for " + diagnostics.getUri() + ": " + diagnostics.getDiagnostics());
        }
        @Override public void showMessage(MessageParams messageParams) { System.err.println("LSP message: " + messageParams.getMessage()); }
        @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) { return CompletableFuture.completedFuture(null); }
        @Override public void logMessage(MessageParams message) { System.err.println("LSP log: " + message.getMessage()); }
    }
}
