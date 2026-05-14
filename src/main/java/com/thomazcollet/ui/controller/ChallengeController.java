package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
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
import javafx.util.Duration;
import java.util.List;

public class ChallengeController {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeController.class);

    @FXML
    private VBox challengesContainer;

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

        if (!actives.isEmpty()) {
            challengesContainer.getChildren().clear();
            for (Challenge challenge : actives) {
                addChallengeCard(challenge);
            }
        } else {
            logger.info("Nenhum desafio ativo encontrado. Mantendo placeholder.");
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
            // 1. Carrega o FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChallengeDialog.fxml"));
            Parent root = loader.load();

            // 2. Obtém o controller do modal
            ChallengeDialogController dialogController = loader.getController();

            // 3. Configura o Stage de forma limpa
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Novo Desafio");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.UTILITY); // Use UTILITY para testes, depois voltamos para UNDECORATED

            Scene scene = new Scene(root);
            dialogStage.setScene(scene);

            // 4. Exibe e aguarda
            logger.info("Exibindo modal de novo desafio...");
            dialogStage.showAndWait();

            // 5. Lógica pós-fechamento
            if (dialogController.isSaveClicked()) {
                Challenge newChallenge = dialogController.getChallenge();
                newChallenge.setProfileId(currentProfileId);
                newChallenge.setStatus(ChallengeStatus.ACTIVE);

                challengeService.createChallenge(newChallenge);
                logger.info("Novo desafio salvo com sucesso: {}", newChallenge.getTitle());

                loadActiveChallenges();
                showToast("✓ Desafio ativado! Boa sorte.");
            }

        } catch (Exception e) {
            // Trocamos para Exception para pegar QUALQUER erro (FXML, NullPointer, etc.)
            logger.error("Erro crítico ao abrir modal: ", e);
            e.printStackTrace();
        }
    }

    @FXML
    private StackPane rootStackPane; // Você vai precisar de um StackPane no seu FXML principal

    private void showToast(String message) {
        Label toast = new Label(message);
        toast.getStyleClass().add("toast-success");
        toast.setOpacity(0);

        // Adiciona o toast no topo do StackPane
        rootStackPane.getChildren().add(toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(20, 0, 0, 0));

        // Animação de entrada
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        // Animação de saída (com delay)
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setDelay(Duration.seconds(2));

        // Remove do StackPane após sumir
        fadeOut.setOnFinished(e -> rootStackPane.getChildren().remove(toast));

        fadeIn.play();
        fadeOut.play();
    }
}