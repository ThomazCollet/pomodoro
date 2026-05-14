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

        // Cálculo do progresso (0.0 a 1.0)
        double progress = (double) challenge.getProgressDays() / challenge.getDurationDays();
        progressChallenge.setProgress(progress);

        lblProgressDays.setText(challenge.getProgressDays() + "/" + challenge.getDurationDays() + " dias");

        renderLives();
    }

    private void renderLives() {
        livesContainer.getChildren().clear();
        int total = challenge.getLivesTotal();
        int remaining = challenge.getLivesRemaining();

        for (int i = 0; i < total; i++) {
            // Trocamos o Circle por uma Label com o caractere de coração
            Label heart = new Label("❤");

            // Adicionamos a classe base para o estilo do coração
            heart.getStyleClass().add("heart-icon");

            if (i < remaining) {
                // Estilo para vida ativa (Red/Peach)
                heart.getStyleClass().add("heart-active");
            } else {
                // Estilo para vida perdida (Cinza/Escuro)
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