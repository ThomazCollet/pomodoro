/**
 * Configuração modular do projeto Pomodoro.
 */
module com.thomazcollet.pomodoro {
    // --- Dependências ---
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media; // ADICIONADO: Necessário para áudio e feedback sonoro
    requires java.sql;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires java.desktop;

    // --- Permissões para JavaFX (FXML) ---
    // Precisamos abrir especificamente o pacote onde os Controllers estão
    opens com.thomazcollet to javafx.fxml;
    opens com.thomazcollet.ui.controller to javafx.fxml;

    // --- Permissões para Testes e Outros Frameworks ---
    opens com.thomazcollet.service;
    opens com.thomazcollet.infra.database;
    opens com.thomazcollet.domain.model;

    // --- Exportação da API ---
    exports com.thomazcollet;
    exports com.thomazcollet.ui.controller; 
    exports com.thomazcollet.domain.model;
    exports com.thomazcollet.domain.exception;
}