package com.thomazcollet.infra.handler;

import com.thomazcollet.domain.exeption.PomodoroException;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class GlobalExceptionHandler {

    public static void handle(Throwable e) {
        if (e instanceof PomodoroException) {
            // Erro de regra de negócio (ex: timer já rodando)
            showWarning("Atenção", e.getMessage());
        } else if (e instanceof IllegalArgumentException) {
            // Erro de entrada (ex: tempo negativo)
            showError("Erro de Configuração", e.getMessage());
        } else {
            // Erro inesperado (erro técnico)
            showError("Erro Inesperado", "Ocorreu um erro interno: " + e.getMessage());
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
        alert.setHeaderText("Algo deu errado");
        alert.setContentText(content);
        alert.showAndWait();
    }
}