package com.thomazcollet.infra.handler;

import com.thomazcollet.domain.exception.DatabaseInitializationException;
import com.thomazcollet.domain.exception.PomodoroException;
import com.thomazcollet.domain.exception.TimerStateException;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralizador de tratamento de exceções da aplicação.
 * Traduz exceções técnicas em feedback visual para o usuário final.
 */
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static void handle(Throwable e) {
        // Logamos sempre a stacktrace completa para debug profissional
        logger.error("Exceção capturada pelo Handler Global: ", e);

        // Garantimos que o alerta seja executado na Thread da UI do JavaFX
        Platform.runLater(() -> {
            if (e instanceof DatabaseInitializationException) {
                handleDatabaseError((DatabaseInitializationException) e);
            } else if (e instanceof TimerStateException) {
                showWarning("Estado do Timer", e.getMessage());
            } else if (e instanceof PomodoroException) {
                showWarning("Regra de Negócio", e.getMessage());
            } else if (e instanceof IllegalArgumentException) {
                showError("Argumento Inválido", e.getMessage());
            } else {
                showError("Erro Crítico", "Ocorreu um erro inesperado no sistema: " + e.getMessage());
            }
        });
    }

    private static void handleDatabaseError(DatabaseInitializationException e) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Falha Crítica de Infraestrutura");
        alert.setHeaderText("Não foi possível inicializar o Banco de Dados");
        alert.setContentText(e.getMessage() + "\n\nA aplicação será encerrada por segurança.");

        // Em erros de inicialização de banco, o app não deve continuar
        alert.showAndWait();
        logger.error("Encerrando aplicação devido a falha crítica no banco.");
        System.exit(1);
    }

    private static void showWarning(String title, String content) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static void showError(String title, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("Erro de Execução");
        alert.setContentText(content);
        alert.showAndWait();
    }
}