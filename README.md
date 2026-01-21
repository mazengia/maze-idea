maze-idea
=========

This project is a small JavaFX app being evolved into an IDE-like application.

What I added recently:
- Background file watcher, in-memory indexer, cache, and a simple search window.
- New Project generator + UI (Spring Boot, React, Angular templates).

How to run
----------

Run the app with Maven:

```bash
mvn -DskipTests=false package
mvn javafx:run
```

Open the app and click "New Project" to create a sample project (Spring Boot, React, or Angular) in the selected location.

Next steps
----------
- Wire the Search window to the global Indexer so searches are performed on the project files.
- Replace the in-memory index with a Lucene-based IndexStore for persistence and scale.
- Add an editor (RichTextFX) and LSP integration for language features.
# maze-idea
