package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;
import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.TimerChangeListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.media.AudioClip;

import java.util.Optional;

/**
 * Controller responsável por gerenciar a visualização do Timer e sincronizar a
 * UI com o PomodoroService.
 */
public class TimerController implements TimerChangeListener {

    @FXML private Label lblTime;
    @FXML private Label lblStatus;
    @FXML private Arc arcProgress;
    @FXML private Button btnStart, btnPause, btnReset, btnSkip;
    @FXML private HBox hboxPips; // Adicione este ID no seu FXML abaixo dos botões

    private PomodoroService pomodoroService;

    public void setPomodoroService(PomodoroService service) {
        this.pomodoroService = service;
        updateDisplay(service.getRemainingSeconds());
        updateSessionPips(); // Inicializa os pips ao carregar o serviço
    }

    @FXML
    public void initialize() {
        if (btnStart != null) btnStart.setOnAction(e -> handleStart());
        if (btnPause != null) btnPause.setOnAction(e -> pomodoroService.pause());
        if (btnReset != null) btnReset.setOnAction(e -> handleReset());
        if (btnSkip != null) btnSkip.setOnAction(e -> handleSkip());
    }

    private void handleStart() {
        try {
            playFeedbackSound(); // Feedback auditivo de início
            pomodoroService.start();
            updateUIState();
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o timer: " + e.getMessage());
        }
    }

    private void handleReset() {
        if (pomodoroService.getTimerState() == TimerState.STOPPED) return;

        boolean wasRunning = pomodoroService.getTimerState() == TimerState.RUNNING;
        if (wasRunning) pomodoroService.pause();

        boolean confirmed = showConfirmationDialog(
                "Reiniciar Cronômetro",
                "Deseja realmente reiniciar a sessão atual? Todo o progresso não salvo será perdido.");

        if (confirmed) {
            pomodoroService.stop();
            lblStatus.setText("TIMER REINICIADO");
            updateDisplay(pomodoroService.getRemainingSeconds());
            arcProgress.setLength(360);
        } else if (wasRunning) {
            pomodoroService.start();
        }
    }

    private void handleSkip() {
        boolean wasRunning = pomodoroService.getTimerState() == TimerState.RUNNING;
        if (wasRunning) pomodoroService.pause();

        boolean confirmed = showConfirmationDialog(
                "Pular Etapa",
                "Deseja avançar para a próxima fase do ciclo?");

        if (confirmed) {
            pomodoroService.skip();
            updateUIState();
            updateDisplay(pomodoroService.getRemainingSeconds());
            arcProgress.setLength(360);
            updateSessionPips(); // Atualiza os pips ao pular
        } else if (wasRunning) {
            pomodoroService.start();
        }
    }

    private void playFeedbackSound() {
        try {
            // Certifique-se de ter um arquivo curto em src/main/resources/sounds/start.wav
            String path = getClass().getResource("/sounds/start.wav").toExternalForm();
            AudioClip clip = new AudioClip(path);
            clip.play();
        } catch (Exception e) {
            // Silencioso se o arquivo não existir, evitando crash
            System.out.println("Aviso: Som de feedback não encontrado.");
        }
    }

    private boolean showConfirmationDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.setGraphic(null);

        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("my-dialog");

        ButtonType btnYes = new ButtonType("Sim", ButtonBar.ButtonData.YES);
        ButtonType btnNo = new ButtonType("Não", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(btnYes, btnNo);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == btnYes;
    }

    private void updateUIState() {
        lblStatus.setText(pomodoroService.getCurrentSessionType().getDescription().toUpperCase());
    }

    @Override
    public void onTick(int secondsRemaining) {
        Platform.runLater(() -> {
            updateDisplay(secondsRemaining);
            updateProgressCircle(secondsRemaining);
        });
    }

    @Override
    public void onFinished() {
        Platform.runLater(() -> {
            lblStatus.setText("SESSÃO CONCLUÍDA!");
            updateDisplay(0);
            arcProgress.setLength(0);
            updateSessionPips(); // Atualiza os pips ao concluir sessão
        });
    }

    private void updateDisplay(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        lblTime.setText(String.format("%02d:%02d", mins, secs));
    }

    private void updateProgressCircle(int seconds) {
        double totalSeconds = pomodoroService.getTotalSessionDuration();
        double percentage = (double) seconds / totalSeconds;
        double angle = percentage * 360;
        arcProgress.setLength(angle);

        String color = (pomodoroService.getCurrentSessionType() == SessionType.FOCUS) ? "#89b4fa" : "#a6e3a1";
        arcProgress.setStyle("-fx-stroke: " + color + ";");
    }

    /**
     * Atualiza visualmente os Pips (indicadores de ciclo).
     * Assume um ciclo de 4 sessões de foco antes da pausa longa.
     */
    public void updateSessionPips() {
        if (hboxPips == null) return;

        Platform.runLater(() -> {
            hboxPips.getChildren().clear();
            int currentSessionInCycle = pomodoroService.getSessionsInCycle(); // Você precisará expor isso no Service
            
            for (int i = 0; i < 4; i++) {
                Circle pip = new Circle(6);
                pip.getStyleClass().add("pip");

                if (i < currentSessionInCycle) {
                    pip.getStyleClass().add("pip-active"); // Sessões já concluídas
                } else if (i == currentSessionInCycle && pomodoroService.getCurrentSessionType() == SessionType.FOCUS) {
                    pip.getStyleClass().add("pip-current"); // Foco atual
                }
                
                hboxPips.getChildren().add(pip);
            }
        });
    }
}