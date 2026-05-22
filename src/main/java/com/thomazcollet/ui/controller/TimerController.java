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

    // CORRIGIDO: Removidos btnStart/btnPause e adicionados os novos controles do
    // Dock
    @FXML
    private Button btnPlayPause, btnReset, btnSkip, btnFloat;
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
        updatePlayPauseButtonIcon(); // Garante o ícone correto ao iniciar
    }

    private void initArcCentering() {
        arcProgress.setManaged(false);

        Runnable center = () -> {
            arcProgress.setCenterX(timerStackPane.getWidth() / 2.0);
            arcProgress.setCenterY(timerStackPane.getHeight() / 2.0);
        };

        timerStackPane.widthProperty().addListener((obs, o, n) -> center.run());
        timerStackPane.heightProperty().addListener((obs, o, n) -> center.run());

        Platform.runLater(center);
    }

    @FXML
    public void initialize() {
        // CORRIGIDO: Configuração do botão unificado Play/Pause
        if (btnPlayPause != null)
            btnPlayPause.setOnAction(e -> handlePlayPauseToggle());

        if (btnReset != null)
            btnReset.setOnAction(e -> handleReset());
        if (btnSkip != null)
            btnSkip.setOnAction(e -> handleSkip());
    }

    /**
     * CORRIGIDO: Gerencia a alternância entre iniciar e pausar no mesmo botão
     */
    private void handlePlayPauseToggle() {
        if (pomodoroService == null)
            return;

        try {
            if (pomodoroService.getTimerState() == TimerState.RUNNING) {
                pomodoroService.pause();
            } else {
                playFeedbackSound();
                pomodoroService.start();
            }
            updateUIState();
            updatePlayPauseButtonIcon();
        } catch (Exception e) {
            System.err.println("Erro ao alternar o estado do timer: " + e.getMessage());
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
            updatePlayPauseButtonIcon(); // Reseta o ícone para Play (▶)
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
            updatePlayPauseButtonIcon(); // Ajusta o ícone se o estado mudar
        } else if (wasRunning) {
            pomodoroService.start();
        }
    }

    @FXML
    private void handleToggleMute() {
        if (pomodoroService == null)
            return;
        pomodoroService.toggleAudioMute();
        boolean muted = pomodoroService.isAudioMuted();
        btnMute.setText(muted ? "🔇" : "🔊");

        // CORRIGIDO: Limpeza e aplicação das classes novas com base no seu CSS
        // customizado
        btnMute.getStyleClass().remove("dock-mute-active");
        if (muted) {
            btnMute.getStyleClass().add("dock-mute-active");
        }
    }

    /**
     * ADICIONADO: Tratamento temporário para o botão de janela flutuante
     * para evitar que o JavaFX quebre ao carregar o FXML.
     */
    @FXML
    private void handleToggleFloatingWindow() {
        System.out.println("Feature futura: Abrindo mini janela flutuante...");
        // Sua lógica de Picture-in-Picture entrará aqui!
    }

    /**
     * ADICIONADO: Sincroniza visualmente o ícone do botão central com o estado do
     * Service
     */
    private void updatePlayPauseButtonIcon() {
        if (pomodoroService == null || btnPlayPause == null)
            return;

        if (pomodoroService.getTimerState() == TimerState.RUNNING) {
            btnPlayPause.setText("⏸");
        } else {
            btnPlayPause.setText("▶");
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
            updatePlayPauseButtonIcon(); // Garante o retorno para o ícone de Play
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