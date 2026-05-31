package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
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
    private HBox cardContainer;
    @FXML
    private Label lblTitle;
    @FXML
    private Label lblDailyGoal; // canto superior direito — status / meta diária
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

    // -----------------------------------------------------------------------
    // RENDERIZAÇÃO PRINCIPAL — roteia pelo status do desafio
    // -----------------------------------------------------------------------

    private void renderCard() {
        lblTitle.setText(challenge.getTitle());

        // Limpa estilos de moldura antes de aplicar
        cardContainer.getStyleClass().removeAll(
                "card-streak", "card-milestone",
                "card-completed", "card-failed");

        if (challenge.getStatus() == ChallengeStatus.COMPLETED) {
            renderCompletedLayout();
        } else if (challenge.getStatus() == ChallengeStatus.FAILED) {
            renderFailedLayout();
        } else {
            // ACTIVE — comportamento original
            if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
                cardContainer.getStyleClass().add("card-milestone");
                renderMilestoneLayout();
            } else {
                cardContainer.getStyleClass().add("card-streak");
                renderStreakLayout();
            }
        }

        // O botão de excluir só aparece em desafios ATIVOS
        if (btnDelete != null) {
            boolean isActive = challenge.getStatus() == ChallengeStatus.ACTIVE;
            btnDelete.setVisible(isActive);
            btnDelete.setManaged(isActive);
        }
    }

    // -----------------------------------------------------------------------
    // ESTADOS ATIVOS (inalterados)
    // -----------------------------------------------------------------------

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

        applyProgressBarStyle("streak-progress", "milestone-progress");
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
        } else if (daysRemaining <= 0) {
            lblDailyGoal.setText("ÚLTIMO DIA! ⚠️");
            lblDailyGoal.setStyle("-fx-text-fill: #f38ba8; -fx-font-weight: bold;");
        } else {
            lblDailyGoal.setText(String.format("Faltam %d dias", daysRemaining));
            lblDailyGoal.setStyle(isUrgent
                    ? "-fx-text-fill: #f38ba8; -fx-font-weight: bold;"
                    : "-fx-text-fill: #89b4fa;");
        }

        applyProgressBarStyle("milestone-progress", "streak-progress");
    }

    // -----------------------------------------------------------------------
    // ESTADOS FINAIS
    // -----------------------------------------------------------------------

    /**
     * Layout para desafios CONCLUÍDOS com sucesso.
     * Card levemente escurecido com selo verde "✓ CONCLUÍDO" no canto superior
     * direito — exatamente onde o usuário já olha para ver o status.
     */
    private void renderCompletedLayout() {
        cardContainer.getStyleClass().add(
                challenge.getType() == ChallengeType.MILESTONE_CHALLENGE
                        ? "card-milestone"
                        : "card-streak");
        cardContainer.getStyleClass().add("card-completed");

        // Barra de progresso cheia
        progressChallenge.setProgress(1.0);

        if (challenge.getType() == ChallengeType.STREAK_CHALLENGE) {
            livesContainer.setVisible(true);
            livesContainer.setManaged(true);
            renderLives();
            lblProgressText.setText(challenge.getDurationDays() + "/" + challenge.getDurationDays() + " dias");
            applyProgressBarStyle("streak-progress", "milestone-progress");
        } else {
            livesContainer.setVisible(false);
            livesContainer.setManaged(false);
            int targetH = challenge.getTargetTotalMinutes() / 60;
            lblProgressText.setText(String.format("%dh / %dh — completo", targetH, targetH));
            applyProgressBarStyle("milestone-progress", "streak-progress");
        }

        // Selo de status no canto superior direito
        lblDailyGoal.setText("✓  CONCLUÍDO");
        lblDailyGoal.setStyle(
                "-fx-text-fill: #a6e3a1; " + // verde Catppuccin
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 12px;");
    }

    /**
     * Layout para desafios que FALHARAM.
     * Tom avermelhado suave — informativo sem punir excessivamente o usuário.
     */
    private void renderFailedLayout() {
        cardContainer.getStyleClass().add(
                challenge.getType() == ChallengeType.MILESTONE_CHALLENGE
                        ? "card-milestone"
                        : "card-streak");
        cardContainer.getStyleClass().add("card-failed");

        // Mostra progresso real até o momento da falha
        double progress = challenge.getType() == ChallengeType.MILESTONE_CHALLENGE
                ? (double) challenge.getAccumulatedMinutes() / Math.max(1, challenge.getTargetTotalMinutes())
                : (double) challenge.getProgressDays() / Math.max(1, challenge.getDurationDays());
        progressChallenge.setProgress(Math.min(progress, 1.0));

        if (challenge.getType() == ChallengeType.STREAK_CHALLENGE) {
            livesContainer.setVisible(true);
            livesContainer.setManaged(true);
            renderLives(); // corações todos perdidos = apagados
            lblProgressText.setText(challenge.getProgressDays() + "/" + challenge.getDurationDays() + " dias");
            applyProgressBarStyle("streak-progress-failed", "milestone-progress");
        } else {
            livesContainer.setVisible(false);
            livesContainer.setManaged(false);
            int accH = challenge.getAccumulatedMinutes() / 60;
            int targetH = challenge.getTargetTotalMinutes() / 60;
            lblProgressText.setText(String.format("%dh / %dh alcançado", accH, targetH));
            applyProgressBarStyle("milestone-progress-failed", "streak-progress");
        }

        // Selo de status no canto superior direito — vermelho suave, não agressivo
        lblDailyGoal.setText("✕  não concluído");
        lblDailyGoal.setStyle(
                "-fx-text-fill: #e07090; " + // vermelho/rosa suave
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 12px;");
    }

    // -----------------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------------

    private void applyProgressBarStyle(String add, String remove) {
        progressChallenge.getStyleClass().remove(remove);
        if (!progressChallenge.getStyleClass().contains(add)) {
            progressChallenge.getStyleClass().add(add);
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

    // -----------------------------------------------------------------------
    // EXCLUSÃO — somente para desafios ativos
    // -----------------------------------------------------------------------

    @FXML
    private void onDelete() {
        if (challenge.getStatus() != ChallengeStatus.ACTIVE)
            return;

        String message = String.format(
                "O progresso do desafio '%s' será perdido permanentemente. Deseja continuar?",
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