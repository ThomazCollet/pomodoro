package com.thomazcollet.service;

import com.thomazcollet.domain.model.TimerState;

public interface TimerChangeListener {
    void onTick(int secondsRemaining);

    void onFinished();

    // Adicione estes dois métodos padrões (default para não quebrar outras classes
    // se houver)
    default void onStateChanged(TimerState newState) {
    }

    default void onMuteChanged(boolean isMuted) {
    }
}