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
    private Stage miniTimerStage;

    public void setPomodoroService(PomodoroService service) {
        this.pomodoroService = service;
        updateDisplay(service.getRemainingSeconds());
        updateSessionPips();
        initArcCentering();
        updatePlayPauseButtonVisual(service.getTimerState());
        updateMuteButtonVisual(service.isAudioMuted());
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
            updateUIState();
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
        if (pomodoroService == null)
            return;
        pomodoroService.toggleAudioMute();
    }

    private void updateMuteButtonVisual(boolean isMuted) {
        if (btnMute == null)
            return;
        btnMute.setText(isMuted ? "🔇" : "🔊");
        btnMute.getStyleClass().remove("dock-mute-active");
        if (isMuted) {
            btnMute.getStyleClass().add("dock-mute-active");
        }
    }

    private void updatePlayPauseButtonVisual(TimerState state) {
        if (btnPlayPause == null)
            return;
        btnPlayPause.setText(state == TimerState.RUNNING ? "⏸" : "▶");
    }

    @FXML
    private void handleToggleFloatingWindow() {
        try {
            Stage mainStage = (Stage) btnFloat.getScene().getWindow();

            if (miniTimerStage != null) {
                if (miniTimerStage.isIconified()) {
                    miniTimerStage.setIconified(false);
                }
                miniTimerStage.toFront();
                miniTimerStage.requestFocus();
                mainStage.setIconified(true);
                return;
            }

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

            double mainX = mainStage.getX();
            double mainY = mainStage.getY();
            double mainWidth = mainStage.getWidth();

            if (mainX <= 0 || mainY <= 0) {
                miniTimerStage.centerOnScreen();
            } else {
                miniTimerStage.setX(mainX + mainWidth + 15);
                miniTimerStage.setY(mainY + 100);
            }

            miniTimerStage.setOnHiding(event -> {
                pomodoroService.removeChangeListener(miniController);
                miniTimerStage = null;
                updatePlayPauseButtonVisual(pomodoroService.getTimerState());
                updateMuteButtonVisual(pomodoroService.isAudioMuted());
            });

            miniTimerStage.show();
            mainStage.setIconified(true);

        } catch (IOException e) {
            System.err.println("Erro ao abrir a janela flutuante: " + e.getMessage());
            e.printStackTrace();
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
        if (lblStatus != null && pomodoroService != null) {
            lblStatus.setText(pomodoroService.getCurrentSessionType().getDescription().toUpperCase());
        }
    }

    private void updateDisplay(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        lblTime.setText(String.format("%02d:%02d", mins, secs));
    }

    private void updateProgressCircle(int seconds) {
        double totalSeconds = pomodoroService.getTotalSessionDuration();
        double percentage = (double) seconds / totalSeconds;
        arcProgress.setLength(percentage * 360);

        String color = (pomodoroService.getCurrentSessionType() == SessionType.FOCUS)
                ? "#89b4fa"
                : "#a6e3a1";
        arcProgress.setStyle("-fx-stroke: " + color + ";");
    }

    /**
     * Renderiza os 4 pips do ciclo Pomodoro com a mesma lógica do
     * MiniTimerController:
     *
     * - pips anteriores ao atual → .pip-active (verde estático — ciclos já feitos)
     * - pip da sessão atual → .pip-focus (azul) se FOCUS
     * .pip-break (verde) se SHORT_BREAK / LONG_BREAK
     * - pips ainda não chegados → .pip (cinza base)
     *
     * O ajuste de activeIndex garante que, durante um descanso, a bolinha do foco
     * já concluído não "avance" prematuramente para o próximo slot vazio.
     */
    public void updateSessionPips() {
        if (hboxPips == null)
            return;

        Platform.runLater(() -> {
            hboxPips.getChildren().clear();

            int currentSessionInCycle = pomodoroService.getSessionsInCycle();
            SessionType currentType = pomodoroService.getCurrentSessionType();

            // Durante descanso o índice "recua" para não adiantar o preenchimento visual
            int activeIndex = (currentType != SessionType.FOCUS)
                    ? currentSessionInCycle - 1
                    : currentSessionInCycle;

            for (int i = 0; i < 4; i++) {
                Circle pip = new Circle(6); // raio 6 igual ao original do TimerView
                pip.getStyleClass().add("pip");

                if (i < activeIndex) {
                    // Ciclos passados — verde estático
                    pip.getStyleClass().add("pip-active");
                } else if (i == activeIndex) {
                    // Sessão corrente — azul (foco) ou verde (descanso)
                    if (currentType == SessionType.FOCUS) {
                        pip.getStyleClass().add("pip-focus");
                    } else {
                        pip.getStyleClass().add("pip-break");
                    }
                }
                // else: pip cinza base — futuro, sem classe extra

                hboxPips.getChildren().add(pip);
            }
        });
    }

    // -----------------------------------------------------------------------
    // Callbacks do TimerChangeListener
    // -----------------------------------------------------------------------

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
            updateUIState();
            updateDisplay(pomodoroService.getRemainingSeconds());
            arcProgress.setLength(360);
            updateSessionPips();
        });
    }

    /**
     * FIX PRINCIPAL: onStateChanged agora chama updateSessionPips().
     *
     * Antes, ao pressionar Play o service disparava RUNNING via onStateChanged,
     * mas os pips não eram redesenhados — ficavam no estado cinza inicial sem
     * nunca receber .pip-focus (azul). O MiniTimerController já fazia isso
     * corretamente; o TimerController estava faltando a chamada.
     */
    @Override
    public void onStateChanged(TimerState newState) {
        Platform.runLater(() -> {
            updatePlayPauseButtonVisual(newState);
            updateSessionPips(); // ← linha que estava faltando
        });
    }

    @Override
    public void onMuteChanged(boolean isMuted) {
        Platform.runLater(() -> updateMuteButtonVisual(isMuted));
    }
}
