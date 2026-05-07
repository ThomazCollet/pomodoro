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
    private FocusSessionService focusSessionService; // Novo mock necessário

    private PomodoroService service;
    private Profile testProfile;

    @BeforeEach
    void setUp() {
        // Criamos um perfil de teste e injetamos o mock do focusSessionService
        testProfile = new Profile("Work", 25, 5, 15);
        testProfile.setId(1L); // Importante setar ID para o service usar

        service = new PomodoroService(testProfile, listener, focusSessionService);
    }

    @Test
    @DisplayName("Deve iniciar o timer e registrar o início da sessão no FocusSessionService")
    void shouldStartTimerAndRegisterInService() {
        service.start();

        assertEquals(TimerState.RUNNING, service.getTimerState());
        // Verifica se o serviço de persistência foi avisado do início
        verify(focusSessionService, times(1)).startSession(eq(1L), eq(SessionType.FOCUS));
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
    @DisplayName("Deve pausar o timer sem finalizar a sessão no banco")
    void shouldPauseTimerWithoutFinalizing() {
        service.start();
        service.pause();

        assertEquals(TimerState.PAUSED, service.getTimerState());
        // Não deve ter chamado o finalize ainda, pois é apenas um pause
        verify(focusSessionService, never()).finalizeCurrentSession(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("Deve finalizar a sessão como não completada ao chamar stop")
    void shouldFinalizeAsNotCompletedWhenStopped() {
        service.start();
        service.stop();

        assertEquals(TimerState.STOPPED, service.getTimerState());
        // Verifica se finalizou a sessão marcando como false (interrompida)
        verify(focusSessionService).finalizeCurrentSession(anyInt(), eq(false));
    }

    @Test
    @DisplayName("Deve manter o estado consistente ao atualizar o perfil")
    void shouldKeepStateConsistentWhenUpdatingProfile() {
        Profile newProfile = new Profile("Short", 10, 2, 5);
        newProfile.setId(1L);

        service.updateProfile(newProfile);

        assertEquals(600, service.getRemainingSeconds()); // 10min
    }
}