package com.thomazcollet.service;

import javafx.scene.media.AudioClip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioServiceTest {

    // Ajustado o nome dos mocks para casar perfeitamente com os atributos da classe
    // original
    @Mock
    private AudioClip timerStartClip;

    @Mock
    private AudioClip timerEndClip;

    private AudioService audioService;

    @BeforeEach
    void setUp() {
        // Instanciação manual explícita para evitar o algoritmo confuso do @InjectMocks
        audioService = new AudioService();

        // Injeção limpa via reflexão diretamente nos alvos corretos
        ReflectionTestUtils.setField(audioService, "timerStartClip", timerStartClip);
        ReflectionTestUtils.setField(audioService, "timerEndClip", timerEndClip);

        audioService.setMuted(false);
    }

    @Test
    @DisplayName("Deve alternar o estado de mudo ao chamar toggleMute")
    void shouldToggleMuteStateCorrectly() {
        assertFalse(audioService.isMuted(), "Deveria iniciar desmutado");

        audioService.toggleMute();
        assertTrue(audioService.isMuted(), "Deveria estar mutado após o primeiro toggle");

        audioService.toggleMute();
        assertFalse(audioService.isMuted(), "Deveria desmutar após o segundo toggle");
    }

    @Test
    @DisplayName("Deve tocar o som de início se não estiver mutado e o arquivo existir")
    void shouldPlayTimerStartWhenNotMuted() {
        audioService.playTimerStart();

        verify(timerStartClip, times(1)).play();
    }

    @Test
    @DisplayName("Não deve tocar o som de início se o serviço estiver mutado")
    void shouldNotPlayTimerStartWhenMuted() {
        audioService.setMuted(true);

        audioService.playTimerStart();

        verify(timerStartClip, never()).play();
    }

    @Test
    @DisplayName("Deve tocar o som de fim se não estiver mutado e o arquivo existir")
    void shouldPlayTimerEndWhenNotMuted() {
        audioService.playTimerEnd();

        verify(timerEndClip, times(1)).play();
    }

    @Test
    @DisplayName("Não deve tocar o som de fim se o serviço estiver mutado")
    void shouldNotPlayTimerEndWhenMuted() {
        audioService.toggleMute();

        audioService.playTimerEnd();

        verify(timerEndClip, never()).play();
    }

    @Test
    @DisplayName("Não deve quebrar a aplicação (Fail-Safe) se o clip de áudio for nulo")
    void shouldHandleNullClipsGracefully() {
        ReflectionTestUtils.setField(audioService, "timerStartClip", null);

        assertDoesNotThrow(() -> audioService.playTimerStart());
    }
}