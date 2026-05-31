package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.model.ChallengeType;
import com.thomazcollet.service.ChallengeService;
import com.thomazcollet.service.ChallengeService.CompletedSummary;

import javafx.animation.FadeTransition;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ChallengeController {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeController.class);

    // --- Seção ATIVOS ---
    @FXML
    private VBox challengesContainer;

    // --- Seção HISTÓRICO ---
    @FXML
    private VBox historySection; // wrapper externo visível sempre
    @FXML
    private Label lblHistorySummary; // barra de resumo "🏆 Você já completou..."
    @FXML
    private Label lblHistoryToggleIcon; // seta "▶" / "▼"
    @FXML
    private VBox historyCardsContainer; // lista colapsável de cards concluídos/falhos

    @FXML
    private StackPane rootStackPane;

    private final ChallengeService challengeService;
    private final Long currentProfileId = 1L;

    /** Controla se a seção de histórico está expandida. Inicia colapsada. */
    private boolean historyExpanded = false;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @FXML
    public void initialize() {
        loadView();
    }

    // -----------------------------------------------------------------------
    // CARREGAMENTO PRINCIPAL
    // -----------------------------------------------------------------------

    /** Ponto de entrada único — carrega ativos e histórico em sequência. */
    private void loadView() {
        loadActiveChallenges();
        loadHistorySection();
    }

    // -----------------------------------------------------------------------
    // SEÇÃO ATIVOS
    // -----------------------------------------------------------------------

    private void loadActiveChallenges() {
        List<Challenge> actives = challengeService.getChallengesByStatus(currentProfileId, ChallengeStatus.ACTIVE);

        challengesContainer.getChildren().clear();

        if (!actives.isEmpty()) {
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

    private void sortChallenges(List<Challenge> challenges) {
        challenges.sort((c1, c2) -> {
            long days1 = calculateDaysRemaining(c1);
            long days2 = calculateDaysRemaining(c2);

            boolean critical1 = days1 <= 1;
            boolean critical2 = days2 <= 1;

            if (critical1 && !critical2)
                return -1;
            if (critical2 && !critical1)
                return 1;

            return Double.compare(calculateProgressPercent(c2), calculateProgressPercent(c1));
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
        VBox emptyBox = new VBox(15);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.getStyleClass().add("empty-state-box");

        Label icon = new Label("🎯");
        icon.getStyleClass().add("empty-state-icon");

        VBox textContainer = new VBox(8);
        textContainer.setAlignment(Pos.CENTER);

        Label mainText = new Label("No momento você não possui nenhum desafio em andamento.");
        mainText.setWrapText(true);
        mainText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #cdd6f4;");

        Label subText = new Label("Que tal começar um agora para elevar sua disciplina?");
        subText.setWrapText(true);
        subText.setStyle("-fx-font-size: 13px; -fx-text-fill: #bac2de;");

        textContainer.getChildren().addAll(mainText, subText);
        emptyBox.getChildren().addAll(icon, textContainer);
        challengesContainer.getChildren().add(emptyBox);
    }

    private void addChallengeCard(Challenge challenge) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChallengeCard.fxml"));
            Node card = loader.load();
            ChallengeCardController cardController = loader.getController();
            cardController.setData(challenge, challengeService, this::loadView);
            challengesContainer.getChildren().add(card);
        } catch (IOException e) {
            logger.error("Erro ao carregar card de desafio: {}", e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // SEÇÃO HISTÓRICO (COLAPSÁVEL)
    // -----------------------------------------------------------------------

    private void loadHistorySection() {
        List<Challenge> finished = challengeService.getFinishedChallenges(currentProfileId);
        CompletedSummary summary = challengeService.getCompletedSummary(currentProfileId);

        // Atualiza o texto do resumo
        if (lblHistorySummary != null) {
            lblHistorySummary.setText(summary.toDisplayText());
        }

        // Garante estado colapsado e popula os cards
        historyExpanded = false;
        if (lblHistoryToggleIcon != null) {
            lblHistoryToggleIcon.setText("▶");
        }
        if (historyCardsContainer != null) {
            historyCardsContainer.setVisible(false);
            historyCardsContainer.setManaged(false);
            historyCardsContainer.getChildren().clear();

            for (Challenge c : finished) {
                addFinishedCard(c);
            }
        }

        // Mostra ou oculta a seção inteira conforme existência de histórico
        if (historySection != null) {
            boolean hasHistory = !finished.isEmpty();
            historySection.setVisible(true);
            historySection.setManaged(true);
        }
    }

    private void addFinishedCard(Challenge challenge) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChallengeCard.fxml"));
            Node card = loader.load();
            ChallengeCardController cardController = loader.getController();
            // Sem callback de refresh: cards do histórico são somente leitura
            cardController.setData(challenge, challengeService, null);
            historyCardsContainer.getChildren().add(card);
        } catch (IOException e) {
            logger.error("Erro ao carregar card de histórico: {}", e.getMessage());
        }
    }

    /**
     * Chamado pelo header da seção de histórico (clique na seta ou no título).
     * Alterna o estado expandido/colapsado com animação suave de fade.
     */
    @FXML
    private void handleToggleHistory() {
        if (historyCardsContainer == null)
            return;

        historyExpanded = !historyExpanded;

        // Roda a seta
        if (lblHistoryToggleIcon != null) {
            RotateTransition rotate = new RotateTransition(Duration.millis(180), lblHistoryToggleIcon);
            rotate.setFromAngle(historyExpanded ? 0 : 90);
            rotate.setToAngle(historyExpanded ? 90 : 0);
            rotate.play();
            lblHistoryToggleIcon.setText(historyExpanded ? "▼" : "▶");
        }

        if (historyExpanded) {
            historyCardsContainer.setVisible(true);
            historyCardsContainer.setManaged(true);
            historyCardsContainer.setOpacity(0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), historyCardsContainer);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        } else {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(180), historyCardsContainer);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                historyCardsContainer.setVisible(false);
                historyCardsContainer.setManaged(false);
            });
            fadeOut.play();
        }
    }

    // -----------------------------------------------------------------------
    // NOVO DESAFIO
    // -----------------------------------------------------------------------

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
                loadView();
            }
        } catch (Exception e) {
            logger.error("Erro crítico ao abrir modal: ", e);
        }
    }

    // -----------------------------------------------------------------------
    // TOAST
    // -----------------------------------------------------------------------

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