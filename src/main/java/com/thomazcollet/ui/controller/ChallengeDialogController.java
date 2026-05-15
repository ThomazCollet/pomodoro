package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.time.LocalDate;

public class ChallengeDialogController {

    @FXML
    private Spinner<Integer> spnMinFocusMinutes;

    @FXML
    private Label lblFocusTimeTranslation; // Nova Label para tradução HH:mm

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
        // Fábrica para Duração (1 a 30 dias, padrão 7)
        SpinnerValueFactory<Integer> durationFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 7);
        spinnerDuration.setValueFactory(durationFactory);

        // META DE FOCO: Mínimo 5 min, Máximo 480 min (8h), Padrão 60, Incremento de 5
        SpinnerValueFactory<Integer> focusFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 480, 60, 5);
        spnMinFocusMinutes.setValueFactory(focusFactory);

        // Listener para atualizar a tradução de tempo (HH:mm) dinamicamente
        spnMinFocusMinutes.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateFocusTimeTranslation(newVal);
        });

        // Inicializa a tradução com o valor padrão
        updateFocusTimeTranslation(60);

        // Inicializa o limite de vidas baseado no padrão (7 dias)
        updateLivesLimit(7);

        // Listener para ajustar o limite de vidas dinamicamente
        spinnerDuration.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateLivesLimit(newVal);
        });
    }

    /**
     * Converte os minutos totais em um formato legível de horas e minutos.
     */
    private void updateFocusTimeTranslation(int totalMinutes) {
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;

        if (hours > 0) {
            lblFocusTimeTranslation.setText(String.format("(%dh %02dm)", hours, minutes));
        } else {
            lblFocusTimeTranslation.setText(String.format("(%dm)", minutes));
        }
    }

    private void updateLivesLimit(int days) {
        int maxLives = (int) Math.ceil(days * 0.35);
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
        if (lblLimitWarning == null)
            return;

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

            // Captura o valor real selecionado pelo usuário
            challenge.setMinFocusMinutesPerDay(spnMinFocusMinutes.getValue());

            // Dados obrigatórios de estado inicial
            challenge.setLivesRemaining(spinnerLives.getValue());
            challenge.setStartDate(LocalDate.now());
            challenge.setStatus(ChallengeStatus.ACTIVE);
            challenge.setProgressDays(0);

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