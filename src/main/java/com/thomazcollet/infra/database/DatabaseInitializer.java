package com.thomazcollet.infra.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.thomazcollet.domain.exception.DatabaseInitializationException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Responsável pela configuração inicial e integridade do banco de dados SQLite.
 * Aplica o princípio Fail-Fast: se o banco falhar, a aplicação não deve
 * iniciar.
 */
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    private static final String URL = "jdbc:sqlite:pomodoro.db";

    public static void initialize() {
        logger.info("Iniciando verificação e configuração do banco de dados...");

        // Adicionados atributos: username (antigo name) e image_path
        String setupSql = """
                PRAGMA foreign_keys = ON;

                CREATE TABLE IF NOT EXISTS profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL,
                    image_path TEXT,
                    work_duration INTEGER NOT NULL DEFAULT 25,
                    short_break INTEGER NOT NULL DEFAULT 5,
                    long_break INTEGER NOT NULL DEFAULT 15,
                    max_streak INTEGER DEFAULT 0,
                    max_focus_day_seconds INTEGER DEFAULT 0,
                    total_focus_sessions INTEGER DEFAULT 0,
                    created_at DATETIME DEFAULT (datetime('now', 'localtime'))
                );

                CREATE TABLE IF NOT EXISTS focus_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    profile_id INTEGER NOT NULL,
                    type TEXT NOT NULL CHECK(type IN ('FOCUS', 'SHORT_BREAK', 'LONG_BREAK')),
                    start_timestamp DATETIME NOT NULL,
                    end_timestamp DATETIME,
                    duration_seconds INTEGER DEFAULT 0,
                    completed BOOLEAN DEFAULT 0,
                    FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                );
                """;
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            for (String sql : setupSql.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql);
                }
            }
            logger.info("Banco de dados verificado com sucesso.");

        } catch (SQLException e) {
            logger.error("ERRO CRÍTICO: Falha ao inicializar o banco de dados SQLite.", e);
            throw new DatabaseInitializationException("Falha ao configurar tabelas iniciais", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}