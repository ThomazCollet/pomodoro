package com.thomazcollet.infra.handler;

import com.thomazcollet.domain.exception.*;
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
        logger.error("Exceção capturada pelo Handler Global: ", e);

        Platform.runLater(() -> {
            if (e instanceof DatabaseInitializationException) {
                handleCriticalError("Banco de Dados", e.getMessage(), true);
            } else if (e instanceof ProfileInitializationException) {
                // Se o perfil falhar na carga inicial, o app não tem contexto para rodar
                handleCriticalError("Perfil de Usuário", e.getMessage(), true);
            } else if (e instanceof ProfileNotFoundException) {
                showWarning("Usuário não Encontrado", e.getMessage());
            } else if (e instanceof TimerStateException) {
                showWarning("Estado do Timer", e.getMessage());
            } else if (e instanceof PomodoroException) {
                showWarning("Regra de Negócio", e.getMessage());
            } else if (e instanceof IllegalArgumentException) {
                showError("Argumento Inválido", e.getMessage());
            } else {
                showError("Erro Crítico", "Ocorreu um erro inesperado: " + e.getMessage());
            }
        });
    }

    /**
     * Centraliza erros que impedem o funcionamento do App (Fail-Fast).
     */
    private static void handleCriticalError(String area, String message, boolean exit) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Falha Crítica de Infraestrutura");
        alert.setHeaderText("Erro em: " + area);
        alert.setContentText(message + (exit ? "\n\nA aplicação será encerrada por segurança." : ""));
        
        alert.showAndWait();
        
        if (exit) {
            logger.error("Encerrando aplicação devido a falha crítica em {}.", area);
            System.exit(1);
        }
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