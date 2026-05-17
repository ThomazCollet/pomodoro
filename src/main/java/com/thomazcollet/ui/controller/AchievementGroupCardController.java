package com.thomazcollet.ui.controller;

import com.thomazcollet.ui.model.AchievementDisplayModel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import java.io.IOException;
import java.util.List;

public class AchievementGroupCardController {

    @FXML
    private Label groupTitleLabel;
    @FXML
    private HBox subCardsContainer;

    /**
     * Inicializa o grupo com um título unificado e monta os 3 sub-cards internos
     */
    public void setGroupData(String groupTitle, List<AchievementDisplayModel> childCards) {
        groupTitleLabel.setText(groupTitle.toUpperCase());
        subCardsContainer.getChildren().clear();

        for (AchievementDisplayModel childModel : childCards) {
            try {
                // CORRIGIDO: Caminho alterado para a pasta correta /fxml/
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/fxml/AchievementSubCard.fxml"));
                StackPane subCardNode = loader.load();

                // Recupera o controller do sub-card injetado para injetar o modelo de dados
                AchievementSubCardController subCardController = loader.getController();
                subCardController.setCardData(childModel);

                // Adiciona o nó na HBox horizontal
                subCardsContainer.getChildren().add(subCardNode);

            } catch (IOException e) {
                throw new RuntimeException("Falha ao carregar componente de sub-card de conquista", e);
            }
        }
    }
}