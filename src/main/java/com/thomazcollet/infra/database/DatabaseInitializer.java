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
 */
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    private static final String URL = "jdbc:sqlite:pomodoro.db";

    public static void initialize() {
        logger.info("Iniciando verificação e configuração do banco de dados...");

        // 1. Script estrutural base (Modificado para incluir as colunas de meta para
        // novos bancos)
        String setupSql = """
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
                                    xp INTEGER DEFAULT 0,
                                    daily_goal_seconds INTEGER NOT NULL DEFAULT 5400,
                                    weekly_goal_seconds INTEGER NOT NULL DEFAULT 27000,
                                    monthly_goal_seconds INTEGER NOT NULL DEFAULT 108000,
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

                                CREATE TABLE IF NOT EXISTS challenges (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                                    profile_id INTEGER NOT NULL,
                                    title TEXT NOT NULL,
                                    type TEXT NOT NULL, -- STREAK_CHALLENGE ou MILESTONE_CHALLENGE
                                    duration_days INTEGER NOT NULL,
                                    min_focus_minutes_per_day INTEGER NOT NULL,
                                    target_total_minutes INTEGER DEFAULT 0, -- Alvo para o modo Intensidade
                                    accumulated_minutes INTEGER DEFAULT 0,  -- Total focado no desafio
                                    today_focus_minutes INTEGER DEFAULT 0,
                                    lives_total INTEGER NOT NULL,
                                    lives_remaining INTEGER NOT NULL,
                                    status TEXT NOT NULL CHECK(status IN ('ACTIVE', 'COMPLETED', 'FAILED')),
                                    start_date DATE NOT NULL,
                                    progress_days INTEGER DEFAULT 0,
                                    FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                                );

                                CREATE TABLE IF NOT EXISTS achievements (
                                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                                    profile_id INTEGER NOT NULL,
                                    achievement_key TEXT NOT NULL, -- Identificador da regra específica
                                    category TEXT NOT NULL CHECK(category IN ('STREAK', 'CHALLENGE', 'DAILY_FOCUS', 'ACHIEVEMENTS', 'RANKING')),
                                    tier TEXT NOT NULL CHECK(tier IN ('BRONZE', 'SILVER', 'GOLD', 'PLATINUM')),
                                    unlocked_at DATETIME DEFAULT (datetime('now', 'localtime')),
                                    UNIQUE(profile_id, achievement_key),
                                    FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                                );

                                CREATE TABLE IF NOT EXISTS streak_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    profile_id INTEGER NOT NULL,
                    duration_days INTEGER NOT NULL, -- Dias da sequência
                    start_date DATE NOT NULL,
                    end_date DATE NOT NULL,
                    FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE CASCADE
                );
                                """;

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {

            // Ativa chaves estrangeiras diretamente na conexão ativa de forma isolada
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Executa a criação das tabelas estruturais base
            for (String sql : setupSql.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql);
                }
            }

            // 2. MIGRATION SYSTEM DEFENSIVO (Para atualizar bancos de dados já existentes
            // localmente)
            applyMigrationsIfNecessary(stmt);

            logger.info("Banco de dados verificado com sucesso.");

        } catch (SQLException e) {
            logger.error("ERRO CRÍTICO: Falha ao inicializar o banco de dados SQLite.", e);
            throw new DatabaseInitializationException("Falha ao configure tabelas iniciais", e);
        }
    }

    /**
     * Adiciona de forma incremental as novas colunas caso o banco de dados do
     * usuário
     * já possua a tabela 'profiles' antiga criada sem elas.
     */
    private static void applyMigrationsIfNecessary(Statement stmt) {
        // Tenta adicionar a coluna daily_goal_seconds
        try {
            stmt.execute("ALTER TABLE profiles ADD COLUMN daily_goal_seconds INTEGER NOT NULL DEFAULT 5400;");
            logger.info("Migração: Coluna 'daily_goal_seconds' adicionada com sucesso.");
        } catch (SQLException e) {
            // Se cair aqui, é porque a coluna já existe (o que é o cenário ideal em
            // execuções futuras)
        }

        // Tenta adicionar a coluna weekly_goal_seconds
        try {
            stmt.execute("ALTER TABLE profiles ADD COLUMN weekly_goal_seconds INTEGER NOT NULL DEFAULT 27000;");
            logger.info("Migração: Coluna 'weekly_goal_seconds' adicionada com sucesso.");
        } catch (SQLException e) {
        }

        // Tenta adicionar a coluna monthly_goal_seconds
        try {
            stmt.execute("ALTER TABLE profiles ADD COLUMN monthly_goal_seconds INTEGER NOT NULL DEFAULT 108000;");
            logger.info("Migração: Coluna 'monthly_goal_seconds' adicionada com sucesso.");
        } catch (SQLException e) {
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}