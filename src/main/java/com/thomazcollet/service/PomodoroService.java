package com.thomazcollet.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thomazcollet.domain.model.TimerState;

import java.util.concurrent.*;

/**
 * Service responsável pela lógica de contagem regressiva do Pomodoro.
 * Implementa o controle de estados e notifica ouvintes via Observer Pattern.
 */
public class PomodoroService {

    private static final Logger logger = LoggerFactory.getLogger(PomodoroService.class);

    private final int initialSeconds;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    
    // TimerListener é a interface que sua UI (Controller) vai implementar
    private final TimerChangeListener listener;

    // Volatile garante que a mudança de estado seja visível imediatamente entre threads
    private volatile int remainingSeconds;
    private volatile TimerState timerState;

    public PomodoroService(int initialSeconds, TimerChangeListener listener) {
        this.initialSeconds = initialSeconds;
        this.remainingSeconds = initialSeconds;
        this.listener = listener;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.timerState = TimerState.STOPPED;
    }

    public void start() {
        if (timerState == TimerState.RUNNING) {
            logger.warn("Tentativa de iniciar timer já em execução.");
            return;
        }

        timerState = TimerState.RUNNING;
        logger.info("Timer iniciado: {} segundos restantes.", remainingSeconds);

        task = executor.scheduleAtFixedRate(() -> {
            if (remainingSeconds <= 0) {
                logger.info("Tempo esgotado.");
                stop();
                listener.onFinished(); // Notifica que acabou
                return;
            }

            remainingSeconds--;
            // Em vez de System.out, usamos o listener para atualizar a UI
            listener.onTick(remainingSeconds);
            
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void pause() {
        if (timerState != TimerState.RUNNING) return;

        cancelTask();
        timerState = TimerState.PAUSED;
        logger.info("Timer pausado em {} segundos.", remainingSeconds);
    }

    public void stop() {
        cancelTask();
        remainingSeconds = initialSeconds;
        timerState = TimerState.STOPPED;
        logger.info("Timer resetado para o estado inicial.");
    }

    private void cancelTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }

    // Importante para encerrar a thread quando fechar o app
    public void shutdown() {
        executor.shutdown();
        logger.info("Executor do Pomodoro finalizado.");
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public TimerState getTimerState() {
        return timerState;
    }
}