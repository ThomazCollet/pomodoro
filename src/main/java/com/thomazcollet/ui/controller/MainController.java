package com.thomazcollet.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Button btnTimer;
    @FXML private Button btnStats;
    @FXML private Button btnSettings;

    @FXML
    public void initialize() {
        // Aqui vamos configurar os cliques nos botões para trocar de tela
        btnTimer.setOnAction(e -> System.out.println("Abrindo Timer..."));
        btnStats.setOnAction(e -> System.out.println("Abrindo Stats..."));
    }
}