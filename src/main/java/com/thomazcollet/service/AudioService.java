package com.thomazcollet.service; // Ajuste para o seu pacote de service

import javafx.scene.media.AudioClip;
import java.net.URL;

public class AudioService {

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
            System.err.println("Erro ao carregar arquivos de áudio: " + e.getMessage());
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