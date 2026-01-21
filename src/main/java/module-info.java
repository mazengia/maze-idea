module com.maze.mazeidea {
    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires java.prefs;
    requires org.fxmisc.richtext;
    requires org.eclipse.lsp4j;
    requires org.eclipse.lsp4j.jsonrpc;

    opens com.maze.mazeidea to javafx.fxml;
    exports com.maze.mazeidea;
}
