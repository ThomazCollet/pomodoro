/**
 * Configuração modular do projeto Pomodoro.
 */
module com.thomazcollet.pomodoro {
    // --- Dependências de Produção ---
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.sql;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires java.desktop;

    // --- Permissões para JavaFX (FXML) ---
    opens com.thomazcollet to javafx.fxml;
    opens com.thomazcollet.ui.controller to javafx.fxml;

    // --- Permissões para Testes e Outros Frameworks ---
    // Deixamos aberto de forma ampla para reflexão, assim o JUnit/Mockito conseguem
    // entrar aqui
    opens com.thomazcollet.service;
    opens com.thomazcollet.infra.database;
    opens com.thomazcollet.domain.model;

    // --- Exportação da API ---
    exports com.thomazcollet;
    exports com.thomazcollet.domain.model;
    exports com.thomazcollet.domain.exception;
    exports com.thomazcollet.service;
    exports com.thomazcollet.ui.controller;
}