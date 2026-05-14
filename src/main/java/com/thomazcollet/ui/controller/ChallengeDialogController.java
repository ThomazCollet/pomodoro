package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus; // Import necessário
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.time.LocalDate; // Import necessário

public class ChallengeDialogController {

    @FXML
    private TextField txtTitle;

    @FXML
    private Spinner<Integer> spinnerDuration;

    @FXML
    private Spinner<Integer> spinnerLives;

    @FXML
    private Label lblLimitWarning;

    private boolean saveClicked = false;
    private Challenge challenge;

    @FXML
    public void initialize() {
        SpinnerValueFactory<Integer> durationFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 7);
        spinnerDuration.setValueFactory(durationFactory);

        updateLivesLimit(7);

        spinnerDuration.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateLivesLimit(newVal);
        });
    }

    private void updateLivesLimit(int days) {
        int maxLives = (int) Math.ceil(days * 0.35); // Ajustado para 35% como conversamos
        int currentVal = (spinnerLives.getValueFactory() != null) ? spinnerLives.getValue() : 1;

        SpinnerValueFactory<Integer> livesFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxLives,
                Math.min(currentVal, maxLives));
        spinnerLives.setValueFactory(livesFactory);

        spinnerLives.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal >= maxLives && maxLives > 0) {
                showLimitWarning();
            }
        });
    }

    private void showLimitWarning() {
        if (lblLimitWarning == null) return;
        lblLimitWarning.setVisible(true);
        lblLimitWarning.setManaged(true);

        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> {
            lblLimitWarning.setVisible(false);
            lblLimitWarning.setManaged(false);
        });
        delay.play();
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            challenge = new Challenge();
            
            // Dados da UI
            challenge.setTitle(txtTitle.getText());
            challenge.setDurationDays(spinnerDuration.getValue());
            challenge.setLivesTotal(spinnerLives.getValue());
            
            // DADOS OBRIGATÓRIOS PARA O SQLITE (O que faltava)
            challenge.setLivesRemaining(spinnerLives.getValue()); // Começa com vidas cheias
            challenge.setStartDate(LocalDate.now());            // Data de início é hoje
            challenge.setStatus(ChallengeStatus.ACTIVE);        // Status inicial ativo
            challenge.setProgressDays(0);                       // Começa com zero progresso
            challenge.setMinFocusMinutesPerDay(25);             // Valor padrão (pode ser ajustado depois)
            
            // O profileId será setado no ChallengeController principal antes de salvar

            saveClicked = true;
            closeStage();
        }
    }

    @FXML
    private void handleCancel() {
        closeStage();
    }

    private boolean isInputValid() {
        return txtTitle.getText() != null && !txtTitle.getText().isBlank();
    }

    private void closeStage() {
        Stage stage = (Stage) txtTitle.getScene().getWindow();
        stage.close();
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public Challenge getChallenge() {
        return challenge;
    }
}