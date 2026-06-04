package com.thomazcollet.service;

import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class AudioService {

    private static final Logger logger = LoggerFactory.getLogger(AudioService.class);

    private AudioClip timerStartClip;
    private AudioClip timerEndClip;
    private boolean muted = false;

    public AudioService() {
        loadSounds();
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public void toggleMute() {
        this.muted = !this.muted;
    }

    private void loadSounds() {
        try {
            URL startUrl = getClass().getResource("/assets/sounds/start_notification.wav");
            URL endUrl = getClass().getResource("/assets/sounds/timer_end.wav");

            if (startUrl != null)
                timerStartClip = new AudioClip(startUrl.toExternalForm());
            if (endUrl != null)
                timerEndClip = new AudioClip(endUrl.toExternalForm());
        } catch (Exception e) {
            logger.error("Erro ao carregar arquivos de áudio.", e);
        }
    }

    public void playTimerStart() {
        if (!muted && timerStartClip != null) {
            timerStartClip.play();
        }
    }

    public void playNotification() {
        if (!muted && timerStartClip != null) {
            timerStartClip.play();
        }
    }

    public void playTimerEnd() {
        if (!muted && timerEndClip != null) {
            timerEndClip.play();
        }
    }
}