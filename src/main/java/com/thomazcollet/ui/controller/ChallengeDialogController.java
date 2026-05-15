package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.model.ChallengeType;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.time.LocalDate;

public class ChallengeDialogController {

    @FXML
    private VBox paneSelection, paneForm, boxStreakFields, boxMilestoneFields, btnStreakType, btnMilestoneType;
    @FXML
    private Label lblSubtitle, lblFocusTimeTranslation, lblLimitWarning;
    @FXML
    private TextField txtTitle;
    @FXML
    private Spinner<Integer> spnMinFocusMinutes, spinnerDuration, spinnerLives, spnTargetTotalHours,
            spinnerMilestoneDuration;

    private boolean saveClicked = false;
    private Challenge challenge;
    private ChallengeType selectedType;

    @FXML
    public void initialize() {
        setupSpinners();
        setupSelectionEvents();

        addHoverAnimation(btnStreakType);
        addHoverAnimation(btnMilestoneType);
    }

    private void setupSpinners() {
        spinnerDuration.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 7));
        spnMinFocusMinutes.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 480, 60, 5));
        spnTargetTotalHours.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 10));
        spinnerMilestoneDuration.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 30));

        spnMinFocusMinutes.valueProperty().addListener((obs, oldVal, newVal) -> updateFocusTimeTranslation(newVal));
        updateFocusTimeTranslation(60);

        spinnerDuration.valueProperty().addListener((obs, oldVal, newVal) -> updateLivesLimit(newVal));
        updateLivesLimit(7);
    }

    private void setupSelectionEvents() {
        btnStreakType.setOnMouseClicked(e -> showForm(ChallengeType.STREAK_CHALLENGE));
        btnMilestoneType.setOnMouseClicked(e -> showForm(ChallengeType.MILESTONE_CHALLENGE));
    }

    private void addHoverAnimation(VBox card) {
        card.setOnMouseEntered(e -> {
            card.setScaleX(1.05);
            card.setScaleY(1.05);
        });
        card.setOnMouseExited(e -> {
            card.setScaleX(1.0);
            card.setScaleY(1.0);
        });
    }

    private void showForm(ChallengeType type) {
        this.selectedType = type;

        paneSelection.setVisible(false);
        paneSelection.setManaged(false);
        
        paneForm.setVisible(true);
        paneForm.setManaged(true);

        boolean isStreak = (type == ChallengeType.STREAK_CHALLENGE);
        boxStreakFields.setVisible(isStreak);
        boxStreakFields.setManaged(isStreak);
        boxMilestoneFields.setVisible(!isStreak);
        boxMilestoneFields.setManaged(!isStreak);

        lblSubtitle.setText(isStreak ? "Configurando Desafio de Constância" : "Configurando Desafio de Intensidade");

        // SOLUÇÃO PARA O CORTE DE TELA:
        // Força o redimensionamento da janela após os componentes serem renderizados
        Platform.runLater(() -> {
            Stage stage = (Stage) paneForm.getScene().getWindow();
            if (stage != null) {
                stage.sizeToScene();
                stage.centerOnScreen(); // Opcional: centraliza após crescer
            }
        });
    }

    private void updateFocusTimeTranslation(int totalMinutes) {
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        lblFocusTimeTranslation
                .setText(hours > 0 ? String.format("(%dh %02dm)", hours, minutes) : String.format("(%dm)", minutes));
    }

    private void updateLivesLimit(int days) {
        int maxLives = (int) Math.ceil(days * 0.35);
        int currentVal = (spinnerLives.getValueFactory() != null) ? spinnerLives.getValue() : 1;
        
        spinnerLives.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxLives, Math.min(currentVal, maxLives)));

        spinnerLives.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal >= maxLives && maxLives > 0)
                showLimitWarning();
        });
    }

    private void showLimitWarning() {
        if (lblLimitWarning == null) return;
        
        lblLimitWarning.setVisible(true);
        lblLimitWarning.setManaged(true);
        
        // Redimensiona para mostrar o aviso sem cortar
        ((Stage) paneForm.getScene().getWindow()).sizeToScene();

        PauseTransition delay = new PauseTransition(Duration.seconds(3));
        delay.setOnFinished(e -> {
            lblLimitWarning.setVisible(false);
            lblLimitWarning.setManaged(false);
            // Volta ao tamanho normal após sumir o aviso
            ((Stage) paneForm.getScene().getWindow()).sizeToScene();
        });
        delay.play();
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            challenge = new Challenge();
            challenge.setTitle(txtTitle.getText());
            challenge.setType(selectedType);
            challenge.setStartDate(LocalDate.now());
            challenge.setStatus(ChallengeStatus.ACTIVE);
            challenge.setProgressDays(0);
            challenge.setAccumulatedMinutes(0);
            challenge.setTodayFocusMinutes(0);

            if (selectedType == ChallengeType.STREAK_CHALLENGE) {
                challenge.setDurationDays(spinnerDuration.getValue());
                challenge.setMinFocusMinutesPerDay(spnMinFocusMinutes.getValue());
                challenge.setLivesTotal(spinnerLives.getValue());
                challenge.setLivesRemaining(spinnerLives.getValue());
            } else {
                challenge.setTargetTotalMinutes(spnTargetTotalHours.getValue() * 60);
                challenge.setDurationDays(spinnerMilestoneDuration.getValue());
                challenge.setLivesTotal(0);
                challenge.setLivesRemaining(0);
            }

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
        Stage stage = (Stage) paneForm.getScene().getWindow();
        stage.close();
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public Challenge getChallenge() {
        return challenge;
    }
}