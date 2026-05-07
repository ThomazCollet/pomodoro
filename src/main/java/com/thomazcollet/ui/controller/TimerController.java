package com.thomazcollet.ui.controller;

import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.TimerChangeListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.Arc;

public class TimerController implements TimerChangeListener {

    @FXML private Label lblTime;
    @FXML private Label lblStatus;
    @FXML private Arc arcProgress;
    @FXML private Button btnStart, btnPause, btnStop;

    private PomodoroService pomodoroService;

    // Método para injetar o serviço no controller
    public void setPomodoroService(PomodoroService service) {
        this.pomodoroService = service;
        updateDisplay(service.getRemainingSeconds());
    }

    @FXML
    public void initialize() {
        btnStart.setOnAction(e -> handleStart());
        btnPause.setOnAction(e -> pomodoroService.pause());
        btnStop.setOnAction(e -> handleStop());
    }

    private void handleStart() {
        try {
            pomodoroService.start();
            lblStatus.setText(pomodoroService.getCurrentSessionType().getDescription().toUpperCase());
        } catch (Exception e) {
            // Trataremos erros de estado aqui depois
            System.err.println(e.getMessage());
        }
    }

    private void handleStop() {
        pomodoroService.stop();
        lblStatus.setText("FOCO INTERROMPIDO");
        updateDisplay(pomodoroService.getRemainingSeconds());
    }

    @Override
    public void onTick(int secondsRemaining) {
        // Platform.runLater garante que a atualização ocorra na thread da UI
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
            // Aqui futuramente chamaremos o som de alerta
        });
    }

    private void updateDisplay(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        lblTime.setText(String.format("%02d:%02d", mins, secs));
    }

    private void updateProgressCircle(int seconds) {
        // Lógica para o arco: (seconds / totalDuration) * 360
        // Por enquanto vamos manter o arco estático ou simplificado
        // Futuramente pegaremos o totalDuration do Profile para um cálculo preciso
    }
}