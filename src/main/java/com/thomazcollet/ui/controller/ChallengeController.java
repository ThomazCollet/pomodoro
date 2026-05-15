package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.model.ChallengeType;
import com.thomazcollet.service.ChallengeService;

import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import javafx.util.Duration;
import java.util.List;

public class ChallengeController {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeController.class);

    @FXML
    private VBox challengesContainer;

    @FXML
    private StackPane rootStackPane;

    private final ChallengeService challengeService;
    private final Long currentProfileId = 1L;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @FXML
    public void initialize() {
        loadActiveChallenges();
    }

    private void loadActiveChallenges() {
        List<Challenge> actives = challengeService.getChallengesByStatus(currentProfileId, ChallengeStatus.ACTIVE);

        challengesContainer.getChildren().clear();

        if (!actives.isEmpty()) {
            // Aplica a ordenação inteligente antes de renderizar
            sortChallenges(actives);

            challengesContainer.setAlignment(Pos.TOP_CENTER);
            challengesContainer.setPadding(new Insets(10, 10, 10, 10));

            for (Challenge challenge : actives) {
                addChallengeCard(challenge);
            }
        } else {
            challengesContainer.setAlignment(Pos.CENTER);
            challengesContainer.setPadding(new Insets(0));
            showPlaceholder();
            logger.info("Nenhum desafio ativo encontrado. Exibindo placeholder centralizado.");
        }
    }

    /**
     * Ordena os desafios por Urgência (prazos críticos) e depois por Progresso
     * (maior primeiro).
     */
    private void sortChallenges(List<Challenge> challenges) {
        challenges.sort((c1, c2) -> {
            long days1 = calculateDaysRemaining(c1);
            long days2 = calculateDaysRemaining(c2);

            // 1. Prioridade de Urgência: Desafios vencendo hoje ou amanhã sobem para o topo
            boolean critical1 = days1 <= 1;
            boolean critical2 = days2 <= 1;

            if (critical1 && !critical2)
                return -1;
            if (critical2 && !critical1)
                return 1;

            // 2. Critério de Desempate: Maior progresso percentual primeiro
            double prog1 = calculateProgressPercent(c1);
            double prog2 = calculateProgressPercent(c2);

            return Double.compare(prog2, prog1);
        });
    }

    private long calculateDaysRemaining(Challenge c) {
        LocalDate endDate = c.getStartDate().plusDays(c.getDurationDays());
        return ChronoUnit.DAYS.between(LocalDate.now(), endDate);
    }

    private double calculateProgressPercent(Challenge c) {
        if (c.getType() == ChallengeType.MILESTONE_CHALLENGE) {
            return (double) c.getAccumulatedMinutes() / c.getTargetTotalMinutes();
        }
        return (double) c.getProgressDays() / c.getDurationDays();
    }

    private void showPlaceholder() {
        try {
            VBox emptyBox = new VBox(15);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.getStyleClass().add("empty-state-box");

            Label icon = new Label("🎯");
            icon.getStyleClass().add("empty-state-icon");

            VBox textContainer = new VBox(8);
            textContainer.setAlignment(Pos.CENTER);

            Label mainText = new Label("No momento você não possui nenhum desafio em andamento.");
            mainText.getStyleClass().add("empty-state-main-text");
            mainText.setWrapText(true);
            mainText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #cdd6f4;");

            Label subText = new Label("Que tal começar um agora para elevar sua disciplina?");
            subText.getStyleClass().add("empty-state-sub-text");
            subText.setWrapText(true);
            subText.setStyle("-fx-font-size: 13px; -fx-text-fill: #bac2de;");

            textContainer.getChildren().addAll(mainText, subText);
            emptyBox.getChildren().addAll(icon, textContainer);

            challengesContainer.getChildren().add(emptyBox);
        } catch (Exception e) {
            logger.error("Erro ao criar placeholder: ", e);
        }
    }

    private void addChallengeCard(Challenge challenge) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChallengeCard.fxml"));
            Node card = loader.load();
            ChallengeCardController cardController = loader.getController();

            cardController.setData(challenge, challengeService, this::loadActiveChallenges);
            challengesContainer.getChildren().add(card);
        } catch (IOException e) {
            logger.error("Erro ao carregar card de desafio: {}", e.getMessage());
        }
    }

    @FXML
    private void handleNewChallenge() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChallengeDialog.fxml"));
            Parent root = loader.load();

            ChallengeDialogController dialogController = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Novo Desafio");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.DECORATED);

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            dialogStage.sizeToScene();
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (dialogController.isSaveClicked()) {
                Challenge newChallenge = dialogController.getChallenge();
                newChallenge.setProfileId(currentProfileId);
                newChallenge.setStatus(ChallengeStatus.ACTIVE);
                challengeService.createChallenge(newChallenge);
                loadActiveChallenges();
            }
        } catch (Exception e) {
            logger.error("Erro crítico ao abrir modal: ", e);
        }
    }

    private void showToast(String message) {
        Label toast = new Label(message);
        toast.getStyleClass().add("toast-success");
        toast.setOpacity(0);

        if (rootStackPane != null) {
            rootStackPane.getChildren().add(toast);
            StackPane.setAlignment(toast, Pos.TOP_CENTER);
            StackPane.setMargin(toast, new Insets(20, 0, 0, 0));

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setDelay(Duration.seconds(2));

            fadeOut.setOnFinished(e -> rootStackPane.getChildren().remove(toast));

            fadeIn.play();
            fadeOut.play();
        }
    }
}