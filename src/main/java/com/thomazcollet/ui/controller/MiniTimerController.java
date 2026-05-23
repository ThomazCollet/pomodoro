package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;
import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.TimerChangeListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller responsável pelo mini widget flutuante do Pomodoro.
 * Implementa suporte a múltiplos listeners e gerência de ciclo por minimização.
 */
public class MiniTimerController implements TimerChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(MiniTimerController.class);

    @FXML
    private VBox rootPane;
    @FXML
    private Label lblTime;
    @FXML
    private HBox hboxPips;
    @FXML
    private Button btnPlayPause;
    @FXML
    private Button btnMute;

    private PomodoroService pomodoroService;
    private Stage miniStage;
    private Stage mainStage;

    private double xOffset = 0;
    private double yOffset = 0;

    public void init(PomodoroService service, Stage miniStage, Stage mainStage) {
        this.pomodoroService = service;
        this.miniStage = miniStage;
        this.mainStage = mainStage;

        // Registro seguro no barramento de múltiplos listeners do Service
        this.pomodoroService.addChangeListener(this);

        // Inicializa o estado visual baseado nos valores vigentes do Service
        updateDisplay(service.getRemainingSeconds());
        updateSessionPips();
        updatePlayPauseButtonIcon();
        updateMuteButtonIcon();

        // Configura o arrastar fluido de forma assíncrona e defensiva
        setupDragAndDrop();

        // Se o usuário fechar a janela pelo atalho do SO (Alt+F4), limpa o listener
        miniStage.setOnCloseRequest(e -> cleanup());
    }

    /**
     * Habilita o recurso de arrastar a janela usando uma estratégia preguiçosa
     * (Lazy)
     * para garantir que a Scene já esteja anexada ao Stage.
     */
    private void setupDragAndDrop() {
        Platform.runLater(() -> {
            if (miniStage != null && miniStage.getScene() != null) {
                var pane = (rootPane != null) ? rootPane : miniStage.getScene().getRoot();

                pane.setOnMousePressed(event -> {
                    xOffset = event.getSceneX();
                    yOffset = event.getSceneY();
                });

                pane.setOnMouseDragged(event -> {
                    miniStage.setX(event.getScreenX() - xOffset);
                    miniStage.setY(event.getScreenY() - yOffset);
                });
            }
        });
    }

    @FXML
    private void handlePlayPauseToggle() {
        if (pomodoroService == null)
            return;

        try {
            if (pomodoroService.getTimerState() == TimerState.RUNNING) {
                pomodoroService.pause();
            } else {
                pomodoroService.start();
            }
            updatePlayPauseButtonIcon();
        } catch (Exception e) {
            logger.error("Erro ao alternar o timer na janela flutuante: {}", e.getMessage());
        }
    }

    @FXML
    private void handleToggleMute() {
        if (pomodoroService == null)
            return;
        pomodoroService.toggleAudioMute();
        updateMuteButtonIcon();
    }

    /**
     * Alterna o contexto visual minimizando o mini widget e trazendo a
     * aplicação principal de volta ao foco, sem destruir o listener.
     */
    @FXML
    private void handleBackToApp() {
        if (mainStage != null && miniStage != null) {
            // 1. Restaura a janela principal da barra de tarefas
            mainStage.setIconified(false);
            mainStage.toFront();
            mainStage.requestFocus();

            // 2. Minimiza o mini timer mantendo ele ativo em background
            miniStage.setIconified(true);
            logger.info("Transição executada: Mini timer minimizado, MainController restaurado.");
        }
    }

    /**
     * Finaliza completamente o ciclo de vida da aplicação a partir do widget.
     */
    @FXML
    private void handleCloseApp() {
        logger.info("Fechamento completo solicitado a partir do Mini Timer.");
        cleanup();
        if (pomodoroService != null) {
            pomodoroService.shutdown();
        }
        Platform.exit();
        System.exit(0);
    }

    /**
     * Remove o listener para evitar vazamentos de memória (Memory Leaks).
     */
    private void cleanup() {
        if (pomodoroService != null) {
            pomodoroService.removeChangeListener(this);
            logger.info("Listener do MiniTimerController removido com sucesso.");
        }
    }

    private void updatePlayPauseButtonIcon() {
        if (pomodoroService == null || btnPlayPause == null)
            return;
        btnPlayPause.setText(pomodoroService.getTimerState() == TimerState.RUNNING ? "⏸" : "▶");
    }

    private void updateMuteButtonIcon() {
        if (pomodoroService == null || btnMute == null)
            return;

        boolean muted = pomodoroService.isAudioMuted();
        btnMute.setText(muted ? "🔇" : "🔊");

        btnMute.getStyleClass().remove("mini-mute-active");
        if (muted) {
            btnMute.getStyleClass().add("mini-mute-active");
        }
    }

    private void updateDisplay(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        lblTime.setText(String.format("%02d:%02d", mins, secs));
    }

    public void updateSessionPips() {
        if (hboxPips == null || pomodoroService == null)
            return;

        Platform.runLater(() -> {
            hboxPips.getChildren().clear();
            int currentSessionInCycle = pomodoroService.getSessionsInCycle();

            for (int i = 0; i < 4; i++) {
                Circle pip = new Circle(4);
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

    // --- Callbacks do Service (Processados de forma segura na UI Thread) ---

    @Override
    public void onTick(int secondsRemaining) {
        Platform.runLater(() -> {
            updateDisplay(secondsRemaining);
            updatePlayPauseButtonIcon();
        });
    }

    @Override
    public void onFinished() {
        Platform.runLater(() -> {
            updateDisplay(pomodoroService.getRemainingSeconds());
            updateSessionPips();
            updatePlayPauseButtonIcon();
        });
    }
}