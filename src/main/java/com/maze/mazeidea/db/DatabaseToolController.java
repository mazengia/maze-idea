package com.maze.mazeidea.db;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseToolController {
    @FXML private TextField jdbcUrlField;
    @FXML private ChoiceBox<String> dbTypeChoice;
    @FXML private ComboBox<String> hostChoice;
    @FXML private TextField portField;
    @FXML private TextField dbNameField;
    @FXML private TextField userField;
    @FXML private PasswordField passwordField;
    @FXML private TextField driverField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Label statusLabel;
    @FXML private ListView<String> tablesList;
    @FXML private TextArea queryArea;
    @FXML private Button runQueryButton;
    @FXML private Label queryStatusLabel;
    @FXML private TextArea queryOutput;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "db-tool"));
    private Connection connection;
    private boolean manualUrl = false;
    private boolean updatingUrl = false;

    @FXML
    public void initialize() {
        if (connectButton != null) connectButton.setOnAction(e -> connect());
        if (disconnectButton != null) disconnectButton.setOnAction(e -> disconnect());
        if (runQueryButton != null) runQueryButton.setOnAction(e -> runQuery());
        setupDefaults();
        updateStatus("Not connected");
    }

    private void setupDefaults() {
        if (dbTypeChoice != null) {
            dbTypeChoice.getItems().setAll(
                    "PostgreSQL",
                    "MySQL",
                    "MariaDB",
                    "Oracle",
                    "SQL Server",
                    "SQLite"
            );
            dbTypeChoice.setValue("PostgreSQL");
            dbTypeChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                manualUrl = false;
                applyDefaultsForType(newV);
            });
        }
        if (hostChoice != null) {
            hostChoice.getItems().setAll("localhost", "127.0.0.1");
            hostChoice.setValue("localhost");
            hostChoice.getEditor().textProperty().addListener((obs, oldV, newV) -> {
                manualUrl = false;
                updateUrlFromFields();
            });
            hostChoice.valueProperty().addListener((obs, oldV, newV) -> {
                manualUrl = false;
                updateUrlFromFields();
            });
        }
        if (portField != null) portField.textProperty().addListener((obs, o, n) -> {
            manualUrl = false;
            updateUrlFromFields();
        });
        if (dbNameField != null) dbNameField.textProperty().addListener((obs, o, n) -> {
            manualUrl = false;
            updateUrlFromFields();
        });
        if (jdbcUrlField != null) {
            jdbcUrlField.textProperty().addListener((obs, oldV, newV) -> {
                if (updatingUrl) return;
                if (newV != null && !newV.equals(oldV)) manualUrl = true;
            });
        }
        applyDefaultsForType(dbTypeChoice != null ? dbTypeChoice.getValue() : "PostgreSQL");
    }

    private void applyDefaultsForType(String type) {
        if ("MySQL".equals(type)) {
            setDefaultPort("3306");
            setDefaultDb("app");
            setDefaultDriver("com.mysql.cj.jdbc.Driver");
        } else if ("MariaDB".equals(type)) {
            setDefaultPort("3306");
            setDefaultDb("app");
            setDefaultDriver("org.mariadb.jdbc.Driver");
        } else if ("Oracle".equals(type)) {
            setDefaultPort("1521");
            setDefaultDb("ORCLCDB");
            setDefaultDriver("oracle.jdbc.OracleDriver");
        } else if ("SQL Server".equals(type)) {
            setDefaultPort("1433");
            setDefaultDb("master");
            setDefaultDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } else if ("SQLite".equals(type)) {
            setDefaultPort("");
            setDefaultDb("app.db");
            setDefaultDriver("org.sqlite.JDBC");
        } else {
            setDefaultPort("5432");
            setDefaultDb("app");
            setDefaultDriver("org.postgresql.Driver");
        }
        updateUrlFromFields();
    }

    private void updateUrlFromFields() {
        if (manualUrl) return;
        String type = dbTypeChoice != null ? dbTypeChoice.getValue() : "PostgreSQL";
        String host = hostChoice != null ? hostChoice.getEditor().getText() : "localhost";
        String port = portField != null ? portField.getText() : "";
        String db = dbNameField != null ? dbNameField.getText() : "app";
        String url;
        if ("MySQL".equals(type)) {
            url = "jdbc:mysql://" + safe(host, "localhost") + ":" + safe(port, "3306") + "/" + safe(db, "app");
        } else if ("MariaDB".equals(type)) {
            url = "jdbc:mariadb://" + safe(host, "localhost") + ":" + safe(port, "3306") + "/" + safe(db, "app");
        } else if ("Oracle".equals(type)) {
            url = "jdbc:oracle:thin:@" + safe(host, "localhost") + ":" + safe(port, "1521") + "/" + safe(db, "ORCLCDB");
        } else if ("SQL Server".equals(type)) {
            url = "jdbc:sqlserver://" + safe(host, "localhost") + ":" + safe(port, "1433") + ";databaseName=" + safe(db, "master");
        } else if ("SQLite".equals(type)) {
            url = "jdbc:sqlite:" + safe(db, "app.db");
        } else {
            url = "jdbc:postgresql://" + safe(host, "localhost") + ":" + safe(port, "5432") + "/" + safe(db, "app");
        }
        if (jdbcUrlField != null) {
            updatingUrl = true;
            jdbcUrlField.setText(url);
            updatingUrl = false;
        }
    }

    private void setDefaultPort(String value) {
        if (portField != null && (portField.getText() == null || portField.getText().isBlank())) {
            portField.setText(value);
        }
    }

    private void setDefaultDb(String value) {
        if (dbNameField != null && (dbNameField.getText() == null || dbNameField.getText().isBlank())) {
            dbNameField.setText(value);
        }
    }

    private void setDefaultDriver(String value) {
        if (driverField != null) {
            driverField.setText(value);
        }
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private void connect() {
        String url = jdbcUrlField != null ? jdbcUrlField.getText() : null;
        if (url == null || url.isBlank()) {
            updateStatus("Enter JDBC URL.");
            return;
        }
        String user = userField != null ? userField.getText() : "";
        String pass = passwordField != null ? passwordField.getText() : "";
        String driver = driverField != null ? driverField.getText() : "";

        dbExecutor.submit(() -> {
            try {
                if (driver != null && !driver.isBlank()) {
                    Class.forName(driver.trim());
                }
                Connection conn = DriverManager.getConnection(url.trim(), user, pass);
                connection = conn;
                Platform.runLater(() -> updateStatus("Connected"));
                loadTables();
            } catch (Exception ex) {
                Platform.runLater(() -> updateStatus("Connect failed: " + ex.getMessage()));
            }
        });
    }

    private void disconnect() {
        dbExecutor.submit(() -> {
            try {
                if (connection != null) connection.close();
            } catch (Exception ignored) {}
            connection = null;
            Platform.runLater(() -> {
                if (tablesList != null) tablesList.getItems().clear();
                updateStatus("Not connected");
            });
        });
    }

    private void loadTables() {
        dbExecutor.submit(() -> {
            if (connection == null) return;
            try {
                DatabaseMetaData meta = connection.getMetaData();
                List<String> tables = new ArrayList<>();
                try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})) {
                    while (rs.next()) {
                        String schema = rs.getString("TABLE_SCHEM");
                        String name = rs.getString("TABLE_NAME");
                        if (schema != null && !schema.isBlank()) tables.add(schema + "." + name);
                        else tables.add(name);
                    }
                }
                Platform.runLater(() -> {
                    if (tablesList != null) {
                        tablesList.getItems().setAll(tables);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> updateStatus("Table load failed: " + ex.getMessage()));
            }
        });
    }

    private void runQuery() {
        String sql = queryArea != null ? queryArea.getText() : "";
        if (sql == null || sql.isBlank()) {
            updateQueryStatus("Enter a query.");
            return;
        }
        if (connection == null) {
            updateQueryStatus("Connect first.");
            return;
        }

        dbExecutor.submit(() -> {
            try (Statement st = connection.createStatement()) {
                boolean hasResult = st.execute(sql);
                if (hasResult) {
                    try (ResultSet rs = st.getResultSet()) {
                        String text = formatResultSet(rs, 50);
                        Platform.runLater(() -> {
                            if (queryOutput != null) queryOutput.setText(text);
                            updateQueryStatus("Query OK");
                        });
                    }
                } else {
                    int count = st.getUpdateCount();
                    Platform.runLater(() -> {
                        if (queryOutput != null) queryOutput.setText("Updated rows: " + count);
                        updateQueryStatus("Update OK");
                    });
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (queryOutput != null) queryOutput.setText("Error: " + ex.getMessage());
                    updateQueryStatus("Query failed");
                });
            }
        });
    }

    private String formatResultSet(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= cols; i++) {
            if (i > 1) sb.append(" | ");
            sb.append(md.getColumnLabel(i));
        }
        sb.append(System.lineSeparator());
        sb.append("-".repeat(Math.max(0, sb.length())));
        sb.append(System.lineSeparator());
        int rows = 0;
        while (rs.next() && rows < maxRows) {
            for (int i = 1; i <= cols; i++) {
                if (i > 1) sb.append(" | ");
                String val = rs.getString(i);
                sb.append(val == null ? "null" : val);
            }
            sb.append(System.lineSeparator());
            rows++;
        }
        if (rs.next()) sb.append("...").append(System.lineSeparator());
        return sb.toString();
    }

    private void updateStatus(String text) {
        if (statusLabel != null) statusLabel.setText(text);
    }

    private void updateQueryStatus(String text) {
        if (queryStatusLabel != null) queryStatusLabel.setText(text);
    }
}
