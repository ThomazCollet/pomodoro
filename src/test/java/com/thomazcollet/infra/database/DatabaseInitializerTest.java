package com.thomazcollet.infra.database;

import org.junit.jupiter.api.*;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DatabaseInitializerTest {

    private static final String DB_PATH = "pomodoro.db";

    @BeforeEach
    void setup() {
        // Deleta o banco antes de cada teste para garantir um ambiente limpo
        File dbFile = new File(DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    @DisplayName("Deve inicializar o banco de dados e criar as tabelas com sucesso")
    void shouldInitializeDatabaseAndCreateTables() throws SQLException {
        // Execução
        DatabaseInitializer.initialize();

        // Verificações
        File dbFile = new File(DB_PATH);
        assertTrue(dbFile.exists(), "O arquivo do banco de dados deve existir.");

        try (Connection conn = DatabaseInitializer.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Verifica tabela profiles
            try (ResultSet rs = metaData.getTables(null, null, "profiles", null)) {
                assertTrue(rs.next(), "A tabela 'profiles' deve ter sido criada.");
            }

            // Verifica tabela focus_sessions
            try (ResultSet rs = metaData.getTables(null, null, "focus_sessions", null)) {
                assertTrue(rs.next(), "A tabela 'focus_sessions' deve ter sido criada.");
            }
        }
    }
}