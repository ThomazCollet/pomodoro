/**
 * Configuração modular do projeto Pomodoro.
 */
module com.thomazcollet.pomodoro {
    // --- Dependências ---
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;

    // --- Permissões para JavaFX (FXML) ---
    opens com.thomazcollet to javafx.fxml;
    opens com.thomazcollet.ui to javafx.fxml;

    // --- Permissões para Testes (JUnit e Reflexão) ---
    // Abrimos os pacotes sem o "to", permitindo acesso a qualquer módulo de teste
    opens com.thomazcollet.service;
    opens com.thomazcollet.infra.database;
    opens com.thomazcollet.domain.model;

    // --- Exportação da API ---
    exports com.thomazcollet;
    exports com.thomazcollet.domain.model;
    exports com.thomazcollet.domain.exception;
}