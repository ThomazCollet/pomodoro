package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.service.ChallengeService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ChallengeController {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeController.class);

    @FXML
    private VBox challengesContainer;

    private final ChallengeService challengeService;
    private final Long currentProfileId = 1L; // Futuramente virá do ProfileService

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @FXML
    public void initialize() {
        loadActiveChallenges();
    }

    private void loadActiveChallenges() {
        List<Challenge> actives = challengeService.getChallengesByStatus(currentProfileId, ChallengeStatus.ACTIVE);

        // Se a lista não estiver vazia, aí sim limpamos o placeholder e adicionamos os
        // cards
        if (!actives.isEmpty()) {
            challengesContainer.getChildren().clear();
            for (Challenge challenge : actives) {
                addChallengeCard(challenge);
            }
        } else {
            // Opcional: Você pode garantir que a mensagem apareça aqui via código
            // ou apenas não dar o clear() se souber que o FXML já tem a mensagem.
            logger.info("Nenhum desafio ativo encontrado. Mantendo placeholder.");
        }
    }

    private void addChallengeCard(Challenge challenge) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChallengeCard.fxml"));

            // Aqui está o truque: precisamos de um controller para cada CARD
            Node card = loader.load();
            ChallengeCardController cardController = loader.getController();

            // Passamos os dados do desafio para o controller do card
            cardController.setData(challenge, challengeService, this::loadActiveChallenges);

            challengesContainer.getChildren().add(card);
        } catch (IOException e) {
            logger.error("Erro ao carregar card de desafio: {}", e.getMessage());
        }
    }

    @FXML
    private void handleNewChallenge() {
        // Próximo passo: abrir o diálogo/modal de criação
        logger.info("Abrindo tela de novo desafio...");
    }
}