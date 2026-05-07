package com.thomazcollet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thomazcollet.domain.exception.TimerStateException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;

import java.util.concurrent.*;

/**
 * Service responsável pela orquestração do tempo e transição de estados.
 * Agora integrado com FocusSessionService para persistência de histórico.
 */
public class PomodoroService {

    private static final Logger logger = LoggerFactory.getLogger(PomodoroService.class);

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private final TimerChangeListener listener;
    private final FocusSessionService focusSessionService;

    private Profile currentProfile;
    private SessionType currentSessionType;

    private volatile int remainingSeconds;
    private int totalSessionDuration;
    private volatile TimerState timerState;

    public PomodoroService(Profile profile, TimerChangeListener listener, FocusSessionService focusSessionService) {
        if (profile == null || listener == null || focusSessionService == null) {
            throw new IllegalArgumentException("Dependências obrigatórias não podem ser nulas.");
        }

        this.currentProfile = profile;
        this.listener = listener;
        this.focusSessionService = focusSessionService;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.timerState = TimerState.STOPPED;

        prepareSession(SessionType.FOCUS);
    }

    private void prepareSession(SessionType type) {
        this.currentSessionType = type;
        this.totalSessionDuration = switch (type) {
            case FOCUS -> currentProfile.getWorkDuration() * 60;
            case SHORT_BREAK -> currentProfile.getShortBreak() * 60;
            case LONG_BREAK -> currentProfile.getLongBreak() * 60;
        };
        this.remainingSeconds = totalSessionDuration;
        logger.info("Sessão preparada: {} ({} segundos)", type.getDescription(), remainingSeconds);
    }

    public void start() {
        if (timerState == TimerState.RUNNING) {
            throw new TimerStateException("O cronômetro já está em execução.");
        }

        // Se estiver iniciando do zero (STOPPED), registra o início no banco
        if (timerState == TimerState.STOPPED) {
            focusSessionService.startSession(currentProfile.getId(), currentSessionType);
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

    private void handleSessionCompletion() {
        cancelTask();
        timerState = TimerState.STOPPED;

        // Finaliza com sucesso (completed = true)
        focusSessionService.finalizeCurrentSession(totalSessionDuration, true);

        logger.info("Sessão de {} finalizada com sucesso.", currentSessionType);

        SessionType nextType = (currentSessionType == SessionType.FOCUS)
                ? SessionType.SHORT_BREAK
                : SessionType.FOCUS;

        listener.onFinished();
        prepareSession(nextType);
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
        if (timerState != TimerState.STOPPED) {
            // Calcula quanto tempo foi percorrido antes de interromper
            int elapsed = totalSessionDuration - remainingSeconds;
            focusSessionService.finalizeCurrentSession(elapsed, false);
        }

        cancelTask();
        timerState = TimerState.STOPPED;
        prepareSession(SessionType.FOCUS);
        logger.info("Timer resetado para o início do ciclo de Foco.");
    }

    private void cancelTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }

    public void skip() {
        cancelTask();
        timerState = TimerState.STOPPED;

        // Define qual seria a próxima sessão
        SessionType nextType = (currentSessionType == SessionType.FOCUS)
                ? SessionType.SHORT_BREAK
                : SessionType.FOCUS;

        prepareSession(nextType);
        listener.onTick(remainingSeconds); // Atualiza a UI com o novo tempo
        logger.info("Sessão pulada. Próxima etapa: {}", nextType);
    }

    public void shutdown() {
        executor.shutdown();
        logger.info("Executor do Pomodoro finalizado.");
    }

    // --- Getters ---

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public TimerState getTimerState() {
        return timerState;
    }

    public SessionType getCurrentSessionType() {
        return currentSessionType;
    }

    public int getTotalSessionDuration() {
        return totalSessionDuration;
    }

    public void updateProfile(Profile newProfile) {
        this.currentProfile = newProfile;
        if (timerState == TimerState.STOPPED) {
            prepareSession(currentSessionType);
        }
    }
}