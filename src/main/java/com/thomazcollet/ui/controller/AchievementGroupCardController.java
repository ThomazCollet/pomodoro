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

    public void setGroupData(String groupTitle, List<AchievementDisplayModel> childCards) {
        if (childCards == null || childCards.isEmpty()) {
            throw new IllegalArgumentException("A lista de sub-cards de um grupo não pode ser nula ou vazia.");
        }

        groupTitleLabel.setText(groupTitle.toUpperCase());

        subCardsContainer.getChildren().clear();
        subCardsContainer.setAlignment(Pos.CENTER);
        subCardsContainer.setSpacing(14.0);

        List<AchievementDisplayModel> sortedCards = new ArrayList<>(childCards);
        sortedCards.sort(Comparator.comparing(AchievementDisplayModel::getTier));

        for (AchievementDisplayModel childModel : sortedCards) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AchievementSubCard.fxml"));
                StackPane subCardNode = loader.load();

                AchievementSubCardController subCardController = loader.getController();
                subCardController.setCardData(childModel);

                // Sem hgrow — o CSS controla a largura fixa do subcard.
                // O HBox centraliza o grupo de 3 cards no meio do groupcard full-width.
                subCardsContainer.getChildren().add(subCardNode);

            } catch (IOException e) {
                throw new RuntimeException(
                        "Falha fatal ao carregar o componente AchievementSubCard dentro do grupo: " + groupTitle, e);
            }
        }
    }
}