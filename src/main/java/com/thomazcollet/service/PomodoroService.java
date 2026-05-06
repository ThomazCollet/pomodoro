package com.thomazcollet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thomazcollet.domain.exception.TimerStateException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;

import java.util.concurrent.*;

/**
 * Service responsável pela orquestração do tempo e transição de estados (Foco/Pausa).
 */
public class PomodoroService {

    private static final Logger logger = LoggerFactory.getLogger(PomodoroService.class);

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private final TimerChangeListener listener;

    private Profile currentProfile;
    private SessionType currentSessionType;
    
    private volatile int remainingSeconds;
    private volatile TimerState timerState;

    public PomodoroService(Profile profile, TimerChangeListener listener) {
        if (profile == null) {
            throw new IllegalArgumentException("O perfil de configuração não pode ser nulo.");
        }
        if (listener == null) {
            throw new IllegalArgumentException("O listener de eventos não pode ser nulo.");
        }

        this.currentProfile = profile;
        this.listener = listener;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.timerState = TimerState.STOPPED;
        
        // Inicia o ciclo por FOCO por padrão
        prepareSession(SessionType.FOCUS);
    }

    /**
     * Configura o tempo do timer baseado no tipo de sessão, sem iniciá-lo.
     */
    private void prepareSession(SessionType type) {
        this.currentSessionType = type;
        this.remainingSeconds = switch (type) {
            case FOCUS -> currentProfile.getWorkDuration() * 60;
            case SHORT_BREAK -> currentProfile.getShortBreak() * 60;
            case LONG_BREAK -> currentProfile.getLongBreak() * 60;
        };
        logger.info("Sessão preparada: {} ({} segundos)", type.getDescription(), remainingSeconds);
    }

    public void start() {
        if (timerState == TimerState.RUNNING) {
            throw new TimerStateException("O cronômetro já está em execução.");
        }

        timerState = TimerState.RUNNING;
        logger.info("Timer iniciado para {}: {} segundos restantes.", currentSessionType, remainingSeconds);

        task = executor.scheduleAtFixedRate(() -> {
            if (remainingSeconds <= 0) {
                handleSessionCompletion();
                return;
            }

            remainingSeconds--;
            listener.onTick(remainingSeconds);
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Gerencia a transição automática de estados ao zerar o tempo.
     */
    private void handleSessionCompletion() {
        cancelTask();
        timerState = TimerState.STOPPED;
        
        logger.info("Sessão de {} finalizada com sucesso.", currentSessionType);
        
        // Lógica de transição: Se era FOCO, vai para PAUSA. Se era PAUSA, volta para FOCO.
        SessionType nextType = (currentSessionType == SessionType.FOCUS) 
                ? SessionType.SHORT_BREAK 
                : SessionType.FOCUS;

        listener.onFinished(); // Notifica a UI para tocar um som ou mostrar alerta
        prepareSession(nextType); // Já deixa o próximo tempo carregado
    }

    public void pause() {
        if (timerState != TimerState.RUNNING) {
            throw new TimerStateException("Apenas um cronômetro em execução pode ser pausado.");
        }

        cancelTask();
        timerState = TimerState.PAUSED;
        logger.info("Timer pausado em {} segundos.", remainingSeconds);
    }

    public void stop() {
        cancelTask();
        timerState = TimerState.STOPPED;
        prepareSession(SessionType.FOCUS); // Reset sempre volta para o início do Foco
        logger.info("Timer resetado para o início do ciclo de Foco.");
    }

    private void cancelTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }

    public void shutdown() {
        executor.shutdown();
        logger.info("Executor do Pomodoro finalizado.");
    }

    // --- Getters para consulta da UI ---

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public TimerState getTimerState() {
        return timerState;
    }

    public SessionType getCurrentSessionType() {
        return currentSessionType;
    }

    public void updateProfile(Profile newProfile) {
        this.currentProfile = newProfile;
        if (timerState == TimerState.STOPPED) {
            prepareSession(currentSessionType);
        }
    }
}