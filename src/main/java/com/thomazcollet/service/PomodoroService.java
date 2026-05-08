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
 */
public class PomodoroService {

    private static final Logger logger = LoggerFactory.getLogger(PomodoroService.class);

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> task;
    private final TimerChangeListener listener;
    private final FocusSessionService focusSessionService;

    private int sessionsInCycle = 0; // Contador de 0 a 3 para os Pips

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

        if (timerState == TimerState.STOPPED) {
            focusSessionService.startSession(currentProfile.getId(), currentSessionType);
        }

        timerState = TimerState.RUNNING;
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

        // Se terminou um FOCO, incrementamos o ciclo para os Pips
        if (currentSessionType == SessionType.FOCUS) {
            incrementCycle();
        }

        focusSessionService.finalizeCurrentSession(totalSessionDuration, true);

        // Lógica de próxima sessão (Se for o 4º foco, vai para Long Break)
        SessionType nextType;
        if (currentSessionType == SessionType.FOCUS) {
            nextType = (sessionsInCycle == 0) ? SessionType.LONG_BREAK : SessionType.SHORT_BREAK;
        } else {
            nextType = SessionType.FOCUS;
        }

        listener.onFinished();
        prepareSession(nextType);
    }

    public void incrementCycle() {
        sessionsInCycle++;
        if (sessionsInCycle > 3) {
            sessionsInCycle = 0;
        }
        logger.info("Ciclo incrementado: {}/4 sessões de foco concluídas.", sessionsInCycle == 0 ? 4 : sessionsInCycle);
    }

    public void skip() {
        cancelTask();
        timerState = TimerState.STOPPED;

        SessionType nextType;

        if (currentSessionType == SessionType.FOCUS) {
            incrementCycle();
            // Se após o incremento o ciclo resetou para 0, significa que completamos o 4º
            // foco
            nextType = (sessionsInCycle == 0) ? SessionType.LONG_BREAK : SessionType.SHORT_BREAK;
        } else {
            nextType = SessionType.FOCUS;
        }

        prepareSession(nextType);
        listener.onTick(remainingSeconds);
        logger.info("Sessão pulada. Próxima etapa: {}", nextType);
    }

    public void pause() {
        if (timerState != TimerState.RUNNING)
            return;
        cancelTask();
        timerState = TimerState.PAUSED;
    }

    public void stop() {
        if (timerState != TimerState.STOPPED) {
            int elapsed = totalSessionDuration - remainingSeconds;
            focusSessionService.finalizeCurrentSession(elapsed, false);
        }
        cancelTask();
        timerState = TimerState.STOPPED;
        sessionsInCycle = 0; // Reset do ciclo visual
        prepareSession(SessionType.FOCUS);
    }

    private void cancelTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Atualiza o perfil do usuário e reajusta as configurações de tempo.
     * Útil quando o usuário altera as preferências de duração.
     */
    public void updateProfile(Profile newProfile) {
        if (newProfile == null)
            return;

        this.currentProfile = newProfile;

        // Se o timer estiver parado, já prepara a próxima sessão com o novo tempo
        if (timerState == TimerState.STOPPED) {
            prepareSession(currentSessionType);
        }

        logger.info("Perfil atualizado no serviço. Novas durações aplicadas.");
    }

    // --- Getters ---
    public int getSessionsInCycle() {
        return sessionsInCycle;
    }

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
}