package com.thomazcollet.service;

public interface TimerChangeListener {
    void onTick(int secondsRemaining);
    void onFinished();
}