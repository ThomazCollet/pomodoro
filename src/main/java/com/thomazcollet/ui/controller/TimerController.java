package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;
import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.TimerChangeListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.shape.Arc;

import java.util.Optional;

/**
 * Controller responsável por gerenciar a visualização do Timer e sincronizar a UI com o PomodoroService.
 */
public class TimerController implements TimerChangeListener {

    @FXML private Label lblTime;
    @FXML private Label lblStatus;
    @FXML private Arc arcProgress;
    @FXML private Button btnStart, btnPause, btnReset, btnSkip;

    private PomodoroService pomodoroService;

    public void setPomodoroService(PomodoroService service) {
        this.pomodoroService = service;
        updateDisplay(service.getRemainingSeconds());
    }

    @FXML
    public void initialize() {
        // Verificações defensivas para evitar NullPointerException caso o FXML esteja desalinhado
        if (btnStart != null) btnStart.setOnAction(e -> handleStart());
        if (btnPause != null) btnPause.setOnAction(e -> pomodoroService.pause());
        if (btnReset != null) btnReset.setOnAction(e -> handleReset());
        if (btnSkip != null) btnSkip.setOnAction(e -> handleSkip());
    }

    private void handleStart() {
        try {
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
                "Deseja realmente reiniciar a sessão atual? Todo o progresso não salvo será perdido."
        );

        if (confirmed) {
            pomodoroService.stop();
            lblStatus.setText("TIMER REINICIADO");
            updateDisplay(pomodoroService.getRemainingSeconds());
            arcProgress.setLength(360);
        } else if (wasRunning) {
            // Retoma automaticamente se o usuário desistir do reset
            pomodoroService.start();
        }
    }

    private void handleSkip() {
        boolean wasRunning = pomodoroService.getTimerState() == TimerState.RUNNING;
        if (wasRunning) pomodoroService.pause();

        boolean confirmed = showConfirmationDialog(
                "Pular Etapa",
                "Deseja avançar para a próxima fase do ciclo?"
        );

        if (confirmed) {
            pomodoroService.skip();
            updateUIState();
            updateDisplay(pomodoroService.getRemainingSeconds());
            arcProgress.setLength(360);
        } else if (wasRunning) {
            // Retoma automaticamente se o usuário desistir de pular
            pomodoroService.start();
        }
    }

    /**
     * Exibe um diálogo de confirmação estilizado e gerencia a suspensão do timer.
     */
    private boolean showConfirmationDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.setGraphic(null); // Remove o ícone padrão para um visual mais limpo

        // Aplica o CSS customizado
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("my-dialog");

        ButtonType btnYes = new ButtonType("Sim");
        ButtonType btnNo = new ButtonType("Não", ButtonBar.ButtonData.CANCEL_CLOSE);
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

        // Atualiza a cor do arco baseada no tipo de sessão
        String color = (pomodoroService.getCurrentSessionType() == SessionType.FOCUS) ? "#89b4fa" : "#a6e3a1";
        arcProgress.setStyle("-fx-stroke: " + color + ";");
    }
}