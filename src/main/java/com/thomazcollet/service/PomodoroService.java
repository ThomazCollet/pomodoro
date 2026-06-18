package com.thomazcollet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thomazcollet.domain.exception.TimerStateException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;

import java.util.List;
import java.util.concurrent.*;

/**
 * Service responsável pela orquestração do tempo e transição de estados.
 * Suporta múltiplos listeners dinamicamente (Thread-Safe).
 */
public class PomodoroService {

    private static final Logger logger = LoggerFactory.getLogger(PomodoroService.class);

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> task;

    private final List<TimerChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final FocusSessionService focusSessionService;
    private final ChallengeService challengeService;
    private final AudioService audioService;
    private final NotificationService notificationService;

    private int sessionsInCycle = 0;
    private Profile currentProfile;
    private SessionType currentSessionType;

    private volatile int remainingSeconds;
    private int totalSessionDuration;
    private volatile TimerState timerState;

    // -----------------------------------------------------------------------
    // CONSTRUTOR ATUALIZADO — aceita NotificationService como dependência
    // opcional (nullable) para não quebrar testes que não usam notificações.
    // -----------------------------------------------------------------------
    public PomodoroService(Profile profile,
            TimerChangeListener initialListener,
            FocusSessionService focusSessionService,
            ChallengeService challengeService,
            AudioService audioService) {
        this(profile, initialListener, focusSessionService, challengeService, audioService, null);
    }

    public PomodoroService(Profile profile,
            TimerChangeListener initialListener,
            FocusSessionService focusSessionService,
            ChallengeService challengeService,
            AudioService audioService,
            NotificationService notificationService) {

        if (profile == null || focusSessionService == null || challengeService == null || audioService == null) {
            throw new IllegalArgumentException("Dependências obrigatórias não podem ser nulas.");
        }

        this.currentProfile = profile;
        this.focusSessionService = focusSessionService;
        this.challengeService = challengeService;
        this.audioService = audioService;
        this.notificationService = notificationService; // nullable — testes existentes não precisam passar
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.timerState = TimerState.STOPPED;

        if (initialListener != null) {
            this.listeners.add(initialListener);
        }

        prepareSession(SessionType.FOCUS);
    }

    public void addChangeListener(TimerChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(TimerChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
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

        audioService.playTimerStart();

        timerState = TimerState.RUNNING;
        notifyStateChanged(timerState);

        task = executor.scheduleAtFixedRate(() -> {
            if (remainingSeconds <= 0) {
                handleSessionCompletion();
                return;
            }
            remainingSeconds--;

            for (TimerChangeListener listener : listeners) {
                listener.onTick(remainingSeconds);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void handleSessionCompletion() {
        cancelTask();
        timerState = TimerState.STOPPED;
        notifyStateChanged(timerState);

        audioService.playTimerEnd();

        if (currentSessionType == SessionType.FOCUS) {
            incrementCycle();

            int minutesSpent = totalSessionDuration / 60;
            if (minutesSpent > 0) {
                challengeService.addFocusMinutesToActiveChallenges(currentProfile.getId(), minutesSpent);
            }

            // Notificação: ciclo pomodoro completo (o 4º foco — sessionsInCycle volta a 0)
            if (sessionsInCycle == 0) {
                notifyFullCycleCompleted();
            }
        }

        focusSessionService.finalizeCurrentSession(totalSessionDuration, true);

        SessionType nextType;
        if (currentSessionType == SessionType.FOCUS) {
            nextType = (sessionsInCycle == 0) ? SessionType.LONG_BREAK : SessionType.SHORT_BREAK;
        } else {
            nextType = SessionType.FOCUS;
        }

        for (TimerChangeListener listener : listeners) {
            listener.onFinished();
        }

        prepareSession(nextType);
    }

    /**
     * Envia notificação parabenizando o usuário por completar um ciclo
     * pomodoro completo (4 sessões de foco seguidas).
     */
    private void notifyFullCycleCompleted() {
        if (notificationService == null)
            return;

        int profileId = currentProfile.getId().intValue();
        notificationService.send(
                profileId,
                "🍅 Ciclo Pomodoro Completo!",
                "Incrível! Você concluiu 4 sessões de foco seguidas. "
                        + "Aproveite o seu descanso estendido — você merece! 💪");
        logger.info("Notificação de ciclo completo enviada para o perfil {}.", profileId);
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
        notifyStateChanged(timerState);

        if (currentSessionType == SessionType.FOCUS) {
            int minutesSpent = totalSessionDuration / 60;
            if (minutesSpent > 0) {
                challengeService.addFocusMinutesToActiveChallenges(currentProfile.getId(), minutesSpent);
            }
            incrementCycle();

            if (sessionsInCycle == 0) {
                notifyFullCycleCompleted();
            }
        }

        // CORREÇÃO: o skip já tratava a sessão como "concluída" para fins de XP
        // de desafios, mas nunca persistia isso no FocusSessionRepository — a
        // sessão ativa ficava com completed=0 para sempre, e por isso nunca
        // aparecia nas estatísticas (que filtram completed=1 em toda consulta).
        focusSessionService.finalizeCurrentSession(totalSessionDuration, true);

        SessionType nextType;
        if (currentSessionType == SessionType.FOCUS) {
            nextType = (sessionsInCycle == 0) ? SessionType.LONG_BREAK : SessionType.SHORT_BREAK;
        } else {
            nextType = SessionType.FOCUS;
        }

        prepareSession(nextType);

        for (TimerChangeListener listener : listeners) {
            listener.onTick(remainingSeconds);
        }
        logger.info("Sessão pulada. Próxima etapa: {}", nextType);
    }

    public void pause() {
        if (timerState != TimerState.RUNNING)
            return;
        cancelTask();
        timerState = TimerState.PAUSED;
        notifyStateChanged(timerState);
    }

    public void stop() {
        if (timerState != TimerState.STOPPED) {
            int elapsedSeconds = totalSessionDuration - remainingSeconds;

            if (currentSessionType == SessionType.FOCUS && elapsedSeconds >= 60) {
                challengeService.addFocusMinutesToActiveChallenges(currentProfile.getId(), elapsedSeconds / 60);
            }

            focusSessionService.finalizeCurrentSession(elapsedSeconds, false);
        }
        cancelTask();
        timerState = TimerState.STOPPED;
        sessionsInCycle = 0;
        notifyStateChanged(timerState);
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

    public void updateProfile(Profile newProfile) {
        if (newProfile == null)
            return;
        this.currentProfile = newProfile;

        if (timerState == TimerState.STOPPED) {
            prepareSession(currentSessionType);
        }
        logger.info("Perfil atualizado no serviço. Novas durações aplicadas.");
    }

    public void toggleAudioMute() {
        audioService.toggleMute();
        notifyMuteChanged(audioService.isMuted());
    }

    private void notifyStateChanged(TimerState newState) {
        for (TimerChangeListener listener : listeners) {
            listener.onStateChanged(newState);
        }
    }

    private void notifyMuteChanged(boolean isMuted) {
        for (TimerChangeListener listener : listeners) {
            listener.onMuteChanged(isMuted);
        }
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

    public boolean isAudioMuted() {
        return audioService.isMuted();
    }
}