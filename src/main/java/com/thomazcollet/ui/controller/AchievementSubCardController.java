package com.thomazcollet.ui.controller;

import com.thomazcollet.ui.model.AchievementDisplayModel;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;

public class AchievementSubCardController {

    @FXML
    private StackPane rootPane;
    @FXML
    private Label tierLabel;
    @FXML
    private Label descriptionLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressText;
    @FXML
    private StackPane overlayPane;
    @FXML
    private Label statusIcon;

    /**
     * Alimenta o card dinamicamente com os dados do modelo de exibição.
     */
    public void setCardData(AchievementDisplayModel model) {
        // Princípio Fail-Fast
        if (model == null) {
            throw new IllegalArgumentException("O modelo de exibição da conquista não pode ser nulo.");
        }

        // 1. Define textos dinâmicos da planilha
        descriptionLabel.setText(model.getDescription());
        progressText.setText(model.getProgressText());
        progressBar.setProgress(model.getProgress());

        // 2. Define o rótulo do Tier com o respectivo Emoji
        String emoji = switch (model.getTier()) {
            case BRONZE -> "🥉 ";
            case SILVER -> "🥈 ";
            case GOLD -> "🥇 ";
            case PLATINUM -> "💎 ";
        };
        tierLabel.setText(emoji + model.getTier().name());

        // 3. Aplica o estado visual limpando estilos antigos e adicionando os novos
        rootPane.getStyleClass().removeAll("sub-card-locked", "sub-card-active", "sub-card-completed");

        switch (model.getState()) {
            case LOCKED -> {
                rootPane.getStyleClass().add("sub-card-locked");
                overlayPane.setVisible(true);
                statusIcon.setText("🔒");
            }
            case ACTIVE -> {
                rootPane.getStyleClass().add("sub-card-active");
                overlayPane.setVisible(false);
            }
            case COMPLETED -> {
                rootPane.getStyleClass().add("sub-card-completed");
                overlayPane.setVisible(true);
                statusIcon.setText("✅");
                progressBar.setProgress(1.0); // Força visual de 100%
            }
        }
    }
}