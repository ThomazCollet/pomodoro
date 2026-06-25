package com.thomazcollet.service;

import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class AudioService {

    private static final Logger logger = LoggerFactory.getLogger(AudioService.class);

    private AudioClip timerStartClip;
    private AudioClip timerEndClip;
    private boolean   muted  = false;
    private int       volume = 100; // 0–100, espelhado do Profile.audioVolume

    public AudioService() {
        loadSounds();
    }

    // ==========================================================================
    // MUDO
    // ==========================================================================

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
    public void toggleMute() { this.muted = !this.muted; }

    // ==========================================================================
    // VOLUME
    // ==========================================================================

    public int getVolume() { return volume; }

    /**
     * Define o volume de todos os clips de áudio gerenciados por este serviço.
     * O valor é normalizado de 0–100 (preferência do usuário) para 0.0–1.0
     * (contrato do {@link AudioClip}).
     *
     * @param volume inteiro entre 0 e 100; valores fora do range são fixados
     *               nos extremos silenciosamente.
     */
    public void setVolume(int volume) {
        this.volume = Math.max(0, Math.min(100, volume));
        double normalized = this.volume / 100.0;

        if (timerStartClip != null) timerStartClip.setVolume(normalized);
        if (timerEndClip   != null) timerEndClip.setVolume(normalized);

        logger.debug("Volume de áudio definido para {}% ({}).", this.volume, normalized);
    }

    // ==========================================================================
    // REPRODUÇÃO
    // ==========================================================================

    public void playTimerStart() {
        if (!muted && timerStartClip != null) timerStartClip.play();
    }

    public void playNotification() {
        if (!muted && timerStartClip != null) timerStartClip.play();
    }

    public void playTimerEnd() {
        if (!muted && timerEndClip != null) timerEndClip.play();
    }

    // ==========================================================================
    // INICIALIZAÇÃO
    // ==========================================================================

    private void loadSounds() {
        try {
            URL startUrl = getClass().getResource("/assets/sounds/start_notification.wav");
            URL endUrl   = getClass().getResource("/assets/sounds/timer_end.wav");

            if (startUrl != null) timerStartClip = new AudioClip(startUrl.toExternalForm());
            if (endUrl   != null) timerEndClip   = new AudioClip(endUrl.toExternalForm());
        } catch (Exception e) {
            logger.error("Erro ao carregar arquivos de áudio.", e);
        }
    }
}