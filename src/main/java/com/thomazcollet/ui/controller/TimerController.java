package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;
import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.TimerChangeListener;
import com.thomazcollet.ui.util.DialogHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.media.AudioClip;

/**
 * Controller responsável por gerenciar a visualização do Timer e sincronizar a
 * UI com o PomodoroService.
 */
public class TimerController implements TimerChangeListener {

    @FXML
    private Label lblTime;
    @FXML
    private Label lblStatus;
    @FXML
    private StackPane timerStackPane;
    @FXML
    private Arc arcProgress;
    @FXML
    private Button btnStart, btnPause, btnReset, btnSkip;
    @FXML
    private Button btnMute;
    @FXML
    private HBox hboxPips;

    private PomodoroService pomodoroService;

    public void setPomodoroService(PomodoroService service) {
        this.pomodoroService = service;
        updateDisplay(service.getRemainingSeconds());
        updateSessionPips();
        initArcCentering();
    }

    /**
     * Remove o Arc do cálculo de layout (setManaged=false) para que a sua
     * bounding box variável não cause deslocamento a cada tick.
     * Em seguida, vincula centerX/centerY ao centro real do StackPane.
     */
    private void initArcCentering() {
        arcProgress.setManaged(false);

        Runnable center = () -> {
            arcProgress.setCenterX(timerStackPane.getWidth()  / 2.0);
            arcProgress.setCenterY(timerStackPane.getHeight() / 2.0);
        };

        // Recentra quando o StackPane for redimensionado (ex: janela maximizada)
        timerStackPane.widthProperty().addListener((obs, o, n)  -> center.run());
        timerStackPane.heightProperty().addListener((obs, o, n) -> center.run());

        // Primeiro layout ainda não aconteceu — aguarda o próximo pulso da UI
        Platform.runLater(center);
    }

    @FXML
    public void initialize() {
        if (btnStart != null)
            btnStart.setOnAction(e -> handleStart());
        if (btnPause != null)
            btnPause.setOnAction(e -> pomodoroService.pause());
        if (btnReset != null)
            btnReset.setOnAction(e -> handleReset());
        if (btnSkip != null)
            btnSkip.setOnAction(e -> handleSkip());
        // btnMute usa onAction="#handleToggleMute" direto no FXML
    }

    private void handleStart() {
        try {
            playFeedbackSound();
            pomodoroService.start();
            updateUIState();
        } catch (Exception e) {
            System.err.println("Erro ao iniciar o timer: " + e.getMessage());
        }
    }

    private void handleReset() {
        if (pomodoroService.getTimerState() == TimerState.STOPPED)
            return;

        boolean wasRunning = pomodoroService.getTimerState() == TimerState.RUNNING;
        if (wasRunning)
            pomodoroService.pause();

        String cssPath = getClass().getResource("/css/style.css").toExternalForm();
        boolean confirmed = DialogHelper.showConfirmation(
                "Reiniciar Cronômetro",
                "Deseja realmente reiniciar a sessão atual? Todo o progresso não salvo será perdido.",
                cssPath);

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
        if (wasRunning)
            pomodoroService.pause();

        String cssPath = getClass().getResource("/css/style.css").toExternalForm();
        boolean confirmed = DialogHelper.showConfirmation(
                "Pular Etapa",
                "Deseja avançar para a próxima fase do ciclo?",
                cssPath);

        if (confirmed) {
            pomodoroService.skip();
            updateUIState();
            updateDisplay(pomodoroService.getRemainingSeconds());
            arcProgress.setLength(360);
            updateSessionPips();
        } else if (wasRunning) {
            pomodoroService.start();
        }
    }

    @FXML
    private void handleToggleMute() {
        if (pomodoroService == null) return;
        pomodoroService.toggleAudioMute();
        boolean muted = pomodoroService.isAudioMuted();
        btnMute.setText(muted ? "🔇" : "🔊");
        btnMute.getStyleClass().removeAll("mute-button-active");
        if (muted) {
            btnMute.getStyleClass().add("mute-button-active");
        }
    }

    private void playFeedbackSound() {
        try {
            String path = getClass().getResource("/sounds/start.wav").toExternalForm();
            AudioClip clip = new AudioClip(path);
            clip.play();
        } catch (Exception e) {
            System.out.println("Aviso: Som de feedback não encontrado.");
        }
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
            updateSessionPips();
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

    public void updateSessionPips() {
        if (hboxPips == null)
            return;

        Platform.runLater(() -> {
            hboxPips.getChildren().clear();
            int currentSessionInCycle = pomodoroService.getSessionsInCycle();

            for (int i = 0; i < 4; i++) {
                Circle pip = new Circle(6);
                pip.getStyleClass().add("pip");

                if (i < currentSessionInCycle) {
                    pip.getStyleClass().add("pip-active");
                } else if (i == currentSessionInCycle && pomodoroService.getCurrentSessionType() == SessionType.FOCUS) {
                    pip.getStyleClass().add("pip-current");
                }

                hboxPips.getChildren().add(pip);
            }
        });
    }
}