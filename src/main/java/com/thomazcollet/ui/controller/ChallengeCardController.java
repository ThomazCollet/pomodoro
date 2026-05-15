package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeType;
import com.thomazcollet.service.ChallengeService;
import com.thomazcollet.ui.util.DialogHelper;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ChallengeCardController {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeCardController.class);

    @FXML
    private Label lblTitle;
    @FXML
    private Label lblDailyGoal; // Usado para o status no canto superior direito
    @FXML
    private ProgressBar progressChallenge;
    @FXML
    private Label lblProgressText; // Texto sobre/abaixo da barra de progresso
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

        if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
            renderMilestoneLayout();
        } else {
            renderStreakLayout();
        }
    }

    private void renderStreakLayout() {
        livesContainer.setVisible(true);
        livesContainer.setManaged(true);

        // Progresso em Dias
        double progress = (double) challenge.getProgressDays() / challenge.getDurationDays();
        progressChallenge.setProgress(progress);
        lblProgressText.setText(challenge.getProgressDays() + "/" + challenge.getDurationDays() + " dias");

        // Meta Diária (Gamificação)
        int currentFocus = challenge.getTodayFocusMinutes();
        int goal = challenge.getMinFocusMinutesPerDay();

        if (currentFocus >= goal) {
            lblDailyGoal.setText(String.format(" • %d/%d min ✓", currentFocus, goal));
            lblDailyGoal.setStyle("-fx-text-fill: #a6e3a1; -fx-font-weight: bold;");
        } else {
            lblDailyGoal.setText(String.format(" • %d/%d min", currentFocus, goal));
            lblDailyGoal.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.5);");
        }

        progressChallenge.getStyleClass().remove("milestone-progress");
        if (!progressChallenge.getStyleClass().contains("streak-progress")) {
            progressChallenge.getStyleClass().add("streak-progress");
        }

        renderLives();
    }

    private void renderMilestoneLayout() {
        livesContainer.setVisible(false);
        livesContainer.setManaged(false);

        // 1. Progresso Visual (Barra)
        double progress = (double) challenge.getAccumulatedMinutes() / challenge.getTargetTotalMinutes();
        progressChallenge.setProgress(progress);

        // 2. Formatação do Progresso (Ex: "1h 33m / 201h")
        // Substituindo o formato decimal anterior (1,3h) por Hh MMm
        int accMin = challenge.getAccumulatedMinutes();
        int h = accMin / 60;
        int m = accMin % 60;
        int targetH = challenge.getTargetTotalMinutes() / 60;

        lblProgressText.setText(String.format("%dh %02dm / %dh", h, m, targetH));

        // 3. Cálculo de Dias Restantes (Foco no Prazo)
        // Substituindo "Faltam Xh Ym" por dias restantes até o fim do prazo
        LocalDate startDate = challenge.getStartDate();
        LocalDate endDate = startDate.plusDays(challenge.getDurationDays());
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), endDate);

        if (accMin >= challenge.getTargetTotalMinutes()) {
            lblDailyGoal.setText("CONCLUÍDO! 🏆");
            lblDailyGoal.setStyle("-fx-text-fill: #f9e2af; -fx-font-weight: bold;");
        } else if (daysRemaining <= 0) {
            lblDailyGoal.setText("Último dia!");
            lblDailyGoal.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
        } else {
            lblDailyGoal.setText(String.format("Faltam %d dias", daysRemaining));
            lblDailyGoal.setStyle("-fx-text-fill: #89b4fa;");
        }

        progressChallenge.getStyleClass().remove("streak-progress");
        if (!progressChallenge.getStyleClass().contains("milestone-progress")) {
            progressChallenge.getStyleClass().add("milestone-progress");
        }
    }

    private void renderLives() {
        livesContainer.getChildren().clear();
        int total = challenge.getLivesTotal();
        int remaining = challenge.getLivesRemaining();

        for (int i = 0; i < total; i++) {
            Label heart = new Label("❤");
            heart.getStyleClass().add("heart-icon");
            heart.getStyleClass().add(i < remaining ? "heart-active" : "heart-lost");
            livesContainer.getChildren().add(heart);
        }
    }

    @FXML
    private void onDelete() {
        String message = String.format("O progresso do desafio '%s' será perdido permanentemente. Deseja continuar?",
                challenge.getTitle());
        String cssPath = getClass().getResource("/css/style.css").toExternalForm();

        if (DialogHelper.showConfirmation("Excluir Desafio", message, cssPath)) {
            try {
                challengeService.deleteChallenge(challenge.getId());
                logger.info("Desafio '{}' removido pelo usuário.", challenge.getTitle());
                if (onUpdateCallback != null) {
                    onUpdateCallback.run();
                }
            } catch (Exception e) {
                logger.error("Erro ao excluir desafio: {}", e.getMessage());
            }
        }
    }
}