package com.thomazcollet.ui.controller;

import com.thomazcollet.ui.model.AchievementDisplayModel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AchievementGroupCardController {

    @FXML
    private Label groupTitleLabel;
    @FXML
    private HBox subCardsContainer;

    /**
     * Inicializa o grupo com um título unificado e monta os sub-cards internos.
     * Garante rigorosamente a ordem visual sequencial: Bronze, Prata, Ouro e
     * Platina,
     * distribuindo-os de forma perfeitamente centralizada e simétrica na UI.
     */
    public void setGroupData(String groupTitle, List<AchievementDisplayModel> childCards) {
        // Princípio Fail-Fast: Validação rígida de dados de entrada
        if (childCards == null || childCards.isEmpty()) {
            throw new IllegalArgumentException("A lista de sub-cards de um grupo não pode ser nula ou vazia.");
        }

        groupTitleLabel.setText(groupTitle.toUpperCase());

        // Limpa o container e força o alinhamento centralizado dos troféus
        subCardsContainer.getChildren().clear();
        subCardsContainer.setAlignment(Pos.CENTER);

        // Garante um espaçamento horizontal equilibrado de 15px entre as medalhas
        subCardsContainer.setSpacing(15.0);

        // Cria uma cópia mutável para evitar alterar a lista original e ordena por
        // ordem natural do Enum Tier (BRONZE, SILVER, GOLD, PLATINUM)
        List<AchievementDisplayModel> sortedCards = new ArrayList<>(childCards);
        sortedCards.sort(Comparator.comparing(AchievementDisplayModel::getTier));

        for (AchievementDisplayModel childModel : sortedCards) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AchievementSubCard.fxml"));
                StackPane subCardNode = loader.load();

                // Recupera o controller do sub-card para injetar o modelo de exibição
                // atualizado
                AchievementSubCardController subCardController = loader.getController();
                subCardController.setCardData(childModel);

                // Adiciona o nó na HBox horizontal de forma centralizada e ordenada
                subCardsContainer.getChildren().add(subCardNode);

            } catch (IOException e) {
                throw new RuntimeException(
                        "Falha fatal ao carregar o componente AchievementSubCard dentro do grupo: " + groupTitle, e);
            }
        }
    }
}