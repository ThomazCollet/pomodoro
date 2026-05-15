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
import javafx.scene.layout.VBox; // Adicionado
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ChallengeCardController {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeCardController.class);

    @FXML
    private HBox cardContainer; // Certifique-se de adicionar este fx:id no VBox raiz do seu FXML
    @FXML
    private Label lblTitle;
    @FXML
    private Label lblDailyGoal;
    @FXML
    private ProgressBar progressChallenge;
    @FXML
    private Label lblProgressText;
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

        // Limpa as classes de moldura antes de aplicar a nova (evita bugs visual ao
        // atualizar)
        cardContainer.getStyleClass().removeAll("card-streak", "card-milestone");

        if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
            cardContainer.getStyleClass().add("card-milestone");
            renderMilestoneLayout();
        } else {
            cardContainer.getStyleClass().add("card-streak");
            renderStreakLayout();
        }
    }

    private void renderStreakLayout() {
        livesContainer.setVisible(true);
        livesContainer.setManaged(true);

        double progress = (double) challenge.getProgressDays() / challenge.getDurationDays();
        progressChallenge.setProgress(progress);
        lblProgressText.setText(challenge.getProgressDays() + "/" + challenge.getDurationDays() + " dias");

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

        int accMin = challenge.getAccumulatedMinutes();
        int targetTotalMin = challenge.getTargetTotalMinutes();
        double progress = (double) accMin / targetTotalMin;
        progressChallenge.setProgress(progress);

        int h = accMin / 60;
        int m = accMin % 60;
        int targetH = targetTotalMin / 60;
        lblProgressText.setText(String.format("%dh %02dm / %dh", h, m, targetH));

        LocalDate endDate = challenge.getStartDate().plusDays(challenge.getDurationDays());
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), endDate);

        long urgencyThreshold = Math.max(2, (long) (challenge.getDurationDays() * 0.1));
        boolean isUrgent = daysRemaining <= urgencyThreshold;

        if (accMin >= targetTotalMin) {
            lblDailyGoal.setText("CONCLUÍDO! 🏆");
            lblDailyGoal.setStyle("-fx-text-fill: #f9e2af; -fx-font-weight: bold;");
        } else {
            if (daysRemaining <= 0) {
                lblDailyGoal.setText("ÚLTIMO DIA! ⚠️");
            } else {
                lblDailyGoal.setText(String.format("Faltam %d dias", daysRemaining));
            }

            if (isUrgent || daysRemaining <= 0) {
                lblDailyGoal.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
            } else {
                lblDailyGoal.setStyle("-fx-text-fill: #89b4fa;");
            }
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