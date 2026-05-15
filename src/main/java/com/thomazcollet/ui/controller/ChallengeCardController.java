package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.service.ChallengeService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChallengeCardController {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeCardController.class);

    @FXML
    private Label lblTitle;
    @FXML
    private Label lblDailyGoal; // Nova label ao lado do título
    @FXML
    private ProgressBar progressChallenge;
    @FXML
    private Label lblProgressDays;
    @FXML
    private HBox livesContainer;
    @FXML
    private Button btnDelete;

    private Challenge challenge;
    private ChallengeService challengeService;
    private Runnable onUpdateCallback;

    public void setData(Challenge challenge, ChallengeService service, Runnable onUpdateCallback) {
        this.challenge = challenge;
        this.challengeService = service;
        this.onUpdateCallback = onUpdateCallback;

        renderCard();
    }

    private void renderCard() {
        lblTitle.setText(challenge.getTitle());

        // Lógica da Meta Diária (Gamificação)
        updateDailyGoalDisplay();

        // Cálculo do progresso total (0.0 a 1.0)
        double progress = (double) challenge.getProgressDays() / challenge.getDurationDays();
        progressChallenge.setProgress(progress);

        lblProgressDays.setText(challenge.getProgressDays() + "/" + challenge.getDurationDays() + " dias");

        renderLives();
    }

    private void updateDailyGoalDisplay() {
        // Supondo que você tenha esse método no Model para pegar o foco do dia atual
        int currentFocus = challenge.getTodayFocusMinutes();
        int goal = challenge.getMinFocusMinutesPerDay();

        if (currentFocus >= goal) {
            // Estado: Meta Concluída (Verde suave + Check)
            lblDailyGoal.setText(String.format(" • %d/%d min ✓", currentFocus, goal));
            lblDailyGoal.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
        } else {
            // Estado: Em progresso (Cinza opaco)
            lblDailyGoal.setText(String.format(" • %d/%d min", currentFocus, goal));
            lblDailyGoal.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.5);");
        }
    }

    private void renderLives() {
        livesContainer.getChildren().clear();
        int total = challenge.getLivesTotal();
        int remaining = challenge.getLivesRemaining();

        for (int i = 0; i < total; i++) {
            Label heart = new Label("❤");
            heart.getStyleClass().add("heart-icon");

            if (i < remaining) {
                heart.getStyleClass().add("heart-active");
            } else {
                heart.getStyleClass().add("heart-lost");
            }

            livesContainer.getChildren().add(heart);
        }
    }

    @FXML
    private void onDelete() {
        try {
            challengeService.deleteChallenge(challenge.getId());
            logger.info("Desafio '{}' excluído via UI.", challenge.getTitle());

            if (onUpdateCallback != null) {
                onUpdateCallback.run();
            }
        } catch (Exception e) {
            logger.error("Erro ao excluir desafio: {}", e.getMessage());
        }
    }
}