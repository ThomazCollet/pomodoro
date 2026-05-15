package com.thomazcollet.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import java.util.Optional;

public class DialogHelper {

    public static boolean showConfirmation(String title, String message, String cssPath) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.setGraphic(null);

        // Aplica o CSS padrão do seu app
        alert.getDialogPane().getStylesheets().add(cssPath);
        alert.getDialogPane().getStyleClass().add("my-dialog");

        ButtonType btnYes = new ButtonType("Sim", ButtonBar.ButtonData.YES);
        ButtonType btnNo = new ButtonType("Não", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(btnYes, btnNo);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == btnYes;
    }
}