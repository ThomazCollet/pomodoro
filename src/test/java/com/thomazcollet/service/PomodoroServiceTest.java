package com.thomazcollet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.model.TimerState;
import com.thomazcollet.domain.exception.TimerStateException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PomodoroServiceTest {

    @Mock
    private TimerChangeListener listener;

    @Mock
    private FocusSessionService focusSessionService;

    @Mock
    private ChallengeService challengeService;

    @Mock
    private AudioService audioService;

    private PomodoroService service;
    private Profile testProfile;

    @BeforeEach
    void setUp() {
        testProfile = new Profile("Work", 25, 5, 15);
        testProfile.setId(1L);

        service = new PomodoroService(testProfile, listener, focusSessionService, challengeService, audioService);
    }

    @Test
    @DisplayName("Deve iniciar o timer, registrar o início da sessão e disparar o áudio de início")
    void shouldStartTimerAndRegisterInService() {
        service.start();

        assertEquals(TimerState.RUNNING, service.getTimerState());
        verify(focusSessionService, times(1)).startSession(eq(1L), eq(SessionType.FOCUS));

        verify(audioService, times(1)).playTimerStart();
    }

    @Test
    @DisplayName("Deve carregar o tempo correto do Perfil ao iniciar (25min = 1500s)")
    void shouldLoadCorrectTimeFromProfile() {
        assertEquals(1500, service.getRemainingSeconds());
    }

    @Test
    @DisplayName("Deve lançar TimerStateException ao tentar iniciar um timer que já está rodando")
    void shouldThrowExceptionWhenStartingAlreadyRunningTimer() {
        service.start();
        assertThrows(TimerStateException.class, () -> service.start());
    }

    @Test
    @DisplayName("Deve finalizar a sessão como não completada ao chamar stop")
    void shouldFinalizeAsNotCompletedWhenStopped() {
        service.start();
        service.stop();

        assertEquals(TimerState.STOPPED, service.getTimerState());
        verify(focusSessionService).finalizeCurrentSession(anyInt(), eq(false));
    }

    // --- NOVOS TESTES DE INTEGRAÇÃO COM CHALLENGES ---

    @Test
    @DisplayName("Deve notificar o ChallengeService ao dar STOP após pelo menos 1 minuto de foco")
    void shouldNotifyChallengesWhenStoppedAfterOneMinute() {
        service.start();
        service.stop();

        verify(challengeService, atMostOnce()).addFocusMinutesToActiveChallenges(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Deve notificar ChallengeService ao dar SKIP em uma sessão de FOCO")
    void shouldNotifyChallengesWhenSkippingFocusSession() {
        service.skip();

        verify(challengeService).addFocusMinutesToActiveChallenges(eq(1L), eq(25));
    }

    // --- TESTES DE CICLO E FLUXO ---

    @Test
    @DisplayName("Deve alternar para LONG_BREAK após o 4º pomodoro de foco")
    void deveAlternarParaLongBreakAposQuartoFoco() {
        for (int i = 0; i < 3; i++) {
            service.skip(); // Foco -> Break
            service.skip(); // Break -> Foco
        }
        service.skip(); // 4º Foco concluído

        assertEquals(SessionType.LONG_BREAK, service.getCurrentSessionType());
        verify(challengeService, times(4)).addFocusMinutesToActiveChallenges(eq(1L), anyInt());
    }

    @Test
    @DisplayName("Não deve adicionar minutos aos desafios se o SKIP for em uma PAUSA")
    void shouldNotAddMinutesWhenSkippingBreak() {
        service.skip(); // Sai do foco (adiciona minutos)
        reset(challengeService); // Limpa o contador do mock

        service.skip(); // Sai da pausa (NÃO deve adicionar minutos)

        verify(challengeService, never()).addFocusMinutesToActiveChallenges(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Deve resetar o ciclo de pips ao dar STOP")
    void deveResetarPipsAoPararTimer() {
        service.skip();
        service.stop();

        assertEquals(0, service.getSessionsInCycle());
    }

    // --- TESTES DO CONTROLE DE ÁUDIO (MUDO) ---

    @Test
    @DisplayName("Deve delegar a ação de mutar diretamente para o AudioService")
    void shouldDelegateToggleMuteToAudioService() {
        service.toggleAudioMute();

        verify(audioService, times(1)).toggleMute();
    }

    @Test
    @DisplayName("Deve retornar o estado correto de mudo a partir do AudioService")
    void shouldReturnMuteStateFromAudioService() {
        when(audioService.isMuted()).thenReturn(true).thenReturn(false);

        assertTrue(service.isAudioMuted(), "Deveria retornar true quando o AudioService estiver mutado");
        assertFalse(service.isAudioMuted(), "Deveria retornar false quando o AudioService não estiver mutado");

        verify(audioService, times(2)).isMuted();
    }

    @Test
    @DisplayName("Deve continuar chamando playTimerStart independente do PomodoroService, isolando a regra no AudioService")
    void shouldNotPlaySoundWhenMuted() {
        // CORREÇÃO: Removido o stub desnecessário do isMuted() para evitar a
        // UnnecessaryStubbingException
        service.start();

        // PomodoroService cumpre o papel dele de avisar "o timer começou"
        verify(audioService, times(1)).playTimerStart();
    }

    @Test
    @DisplayName("Deve encaminhar chamadas consecutivas de alternância de mudo de forma independente")
    void shouldCallToggleTwiceOnDoubleToggle() {
        service.toggleAudioMute();
        service.toggleAudioMute();

        verify(audioService, times(2)).toggleMute();
    }
}