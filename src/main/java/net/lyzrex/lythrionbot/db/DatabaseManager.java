package net.lyzrex.lythrionbot.db;

import net.lyzrex.lythrionbot.Env;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseManager {

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public DatabaseManager() throws Exception {
        String envUrl = Env.get("DB_URL");
        String envUser = Env.get("DB_USER");
        String envPass = Env.get("DB_PASSWORD");

        if (envUrl != null && !envUrl.isBlank()) {
            this.jdbcUrl = envUrl;
            this.user = envUser != null ? envUser : "";
            this.password = envPass != null ? envPass : "";
            testConnection();
            System.out.println("Connected to MySQL via environment DB_URL.");
            return;
        }

        File file = new File("database.properties");
        if (!file.exists()) {
            writeDefaultDatabaseProperties(file);
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        }

        this.jdbcUrl = props.getProperty(
                "jdbcUrl",
                "jdbc:mysql://127.0.0.1:3306/murmelapi?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8"
        );
        this.user = props.getProperty("user", "root");
        this.password = props.getProperty("password", "");

        testConnection();
        System.out.println("Connected to MySQL: " + jdbcUrl);
    }

    private void writeDefaultDatabaseProperties(File file) throws Exception {
        String content = """
                jdbcUrl=jdbc:mysql://127.0.0.1:3306/murmelapi?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8
                user=root
                password=
                """;

        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            fw.write(content);
        }
    }

    private void testConnection() throws SQLException {
        try (Connection ignored = getConnection()) {
            // just test once
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUser() {
        return user;
    }
}
