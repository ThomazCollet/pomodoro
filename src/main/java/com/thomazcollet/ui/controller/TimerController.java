package com.thomazcollet.ui.controller;

import java.io.IOException;

import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;
import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.TimerChangeListener;
import com.thomazcollet.ui.util.DialogHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.media.AudioClip;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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
    private Button btnPlayPause, btnReset, btnSkip, btnFloat;
    @FXML
    private Button btnMute;
    @FXML
    private HBox hboxPips;

    private PomodoroService pomodoroService;
    private Stage miniTimerStage; // Guardará a instância única da mini janela

    public void setPomodoroService(PomodoroService service) {
        this.pomodoroService = service;
        updateDisplay(service.getRemainingSeconds());
        updateSessionPips();
        initArcCentering();
        updatePlayPauseButtonIcon();
        updateMuteButtonVisual(); // Sincroniza o estado inicial do mute
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
        if (btnPlayPause != null)
            btnPlayPause.setOnAction(e -> handlePlayPauseToggle());

        if (btnReset != null)
            btnReset.setOnAction(e -> handleReset());
        if (btnSkip != null)
            btnSkip.setOnAction(e -> handleSkip());
    }

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
            updateUIState(); // REFACTOR: Usa o estado atual do service (Foco)
            updateDisplay(pomodoroService.getRemainingSeconds());
            arcProgress.setLength(360);
            updatePlayPauseButtonIcon();
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
            updatePlayPauseButtonIcon();
        } else if (wasRunning) {
            pomodoroService.start();
        }
    }

    @FXML
    private void handleToggleMute() {
        if (pomodoroService == null)
            return;
        pomodoroService.toggleAudioMute();
        updateMuteButtonVisual();
    }

    /**
     * REFACTOR: Atualiza os elementos visuais do mute com base no Service.
     * Isolado para poder ser chamado também ao retornar da mini-janela.
     */
    private void updateMuteButtonVisual() {
        boolean muted = pomodoroService.isAudioMuted();
        btnMute.setText(muted ? "🔇" : "🔊");

        btnMute.getStyleClass().remove("dock-mute-active");
        if (muted) {
            btnMute.getStyleClass().add("dock-mute-active");
        }
    }

    /**
     * REFACTOR: Cria e exibe a janela flutuante no estilo Picture-in-Picture.
     */
    @FXML
    private void handleToggleFloatingWindow() {
        try {
            Stage mainStage = (Stage) btnFloat.getScene().getWindow();

            // REGRA 1: Se a mini janela JÁ EXISTE, apenas manipula os estados de
            // minimizar/restaurar
            if (miniTimerStage != null) {
                if (miniTimerStage.isIconified()) {
                    miniTimerStage.setIconified(false); // Restaura se estava minimizada
                }
                miniTimerStage.toFront();
                miniTimerStage.requestFocus();

                mainStage.setIconified(true); // Minimiza a tela principal
                return;
            }

            // REGRA 2: Se é a primeira vez clicando, cria a mini tela normalmente
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mini-timer-view.fxml"));
            Parent miniRoot = loader.load();

            miniTimerStage = new Stage();
            miniTimerStage.initStyle(StageStyle.TRANSPARENT);
            miniTimerStage.setAlwaysOnTop(true);

            Scene scene = new Scene(miniRoot);
            scene.setFill(Color.TRANSPARENT);

            var cssUrl = getClass().getResource("/css/style.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            miniTimerStage.setScene(scene);

            MiniTimerController miniController = loader.getController();
            miniController.init(pomodoroService, miniTimerStage, mainStage);

            // Posicionamento seguro
            double mainX = mainStage.getX();
            double mainY = mainStage.getY();
            double mainWidth = mainStage.getWidth();

            if (mainX <= 0 || mainY <= 0) {
                miniTimerStage.centerOnScreen();
            } else {
                miniTimerStage.setX(mainX + mainWidth + 15);
                miniTimerStage.setY(mainY + 100);
            }

            // Se o usuário fechar a mini tela pelo '✕', limpamos a referência
            miniTimerStage.setOnHiding(event -> {
                // Remove o listener para evitar vazamento de memória (Memory Leak)
                pomodoroService.removeChangeListener(miniController);
                miniTimerStage = null;
            });

            // Exibe a mini tela e MINIMIZA a principal de forma elegante
            miniTimerStage.show();
            mainStage.setIconified(true);

        } catch (IOException e) {
            System.err.println("Erro ao abrir a janela flutuante: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updatePlayPauseButtonIcon() {
        if (pomodoroService == null || btnPlayPause == null)
            return;
        btnPlayPause.setText(pomodoroService.getTimerState() == TimerState.RUNNING ? "⏸" : "▶");
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
            // REFACTOR: Em vez de fixar texto estático, lê o estado atualizado do Service
            // (que já realizou o auto-advance em background)
            updateUIState();
            updateDisplay(pomodoroService.getRemainingSeconds());
            arcProgress.setLength(360);
            updateSessionPips();
            updatePlayPauseButtonIcon();
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