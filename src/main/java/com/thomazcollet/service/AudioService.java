package com.thomazcollet.service; // Ajuste para o seu pacote de service

import javafx.scene.media.AudioClip;
import java.net.URL;

public class AudioService {

    private AudioClip timerStartClip;
    private AudioClip timerEndClip;

    public AudioService() {
        loadSounds();
    }

    private void loadSounds() {
        try {
            URL startUrl = getClass().getResource("/assets/sounds/start_notification.wav");
            URL endUrl = getClass().getResource("/assets/sounds/timer_end.wav");

            if (startUrl != null) timerStartClip = new AudioClip(startUrl.toExternalForm());
            if (endUrl != null) timerEndClip = new AudioClip(endUrl.toExternalForm());
        } catch (Exception e) {
            System.err.println("Erro ao carregar arquivos de áudio: " + e.getMessage());
        }
    }

    public void playTimerStart() {
        if (timerStartClip != null) {
            timerStartClip.play();
        }
    }

    public void playNotification() {
        // Como você optou por usar o mesmo arquivo, ele reaproveita o mesmo clip
        if (timerStartClip != null) {
            timerStartClip.play();
        }
    }

    public void playTimerEnd() {
        if (timerEndClip != null) {
            timerEndClip.play();
        }
    }
}