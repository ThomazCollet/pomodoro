package com.thomazcollet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.thomazcollet.domain.exception.TimerStateException;
import com.thomazcollet.domain.model.TimerState;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PomodoroServiceTest {

    @Mock
    private TimerChangeListener listener;

    private PomodoroService service;
    private final int DEFAULT_TIME = 1500; // 25 minutos em segundos

    @BeforeEach
    void setUp() {
        // Inicializa o serviço com o listener mockado antes de cada teste
        service = new PomodoroService(DEFAULT_TIME, listener);
    }

    @Test
    @DisplayName("Deve iniciar o timer com sucesso e mudar o estado para RUNNING")
    void shouldStartTimerSuccessfullyAndChangeStateToRunning() {
        service.start();
        
        assertEquals(TimerState.RUNNING, service.getTimerState());
    }

    @Test
    @DisplayName("Deve lançar TimerStateException ao tentar iniciar um timer que já está rodando")
    void shouldThrowExceptionWhenStartingAlreadyRunningTimer() {
        service.start();
        
        // Verifica se a segunda chamada ao start() lança a exceção de negócio
        assertThrows(TimerStateException.class, () -> service.start());
    }

    @Test
    @DisplayName("Deve pausar o timer corretamente quando ele estiver rodando")
    void shouldPauseTimerWhenRunning() {
        service.start();
        service.pause();
        
        assertEquals(TimerState.PAUSED, service.getTimerState());
    }

    @Test
    @DisplayName("Deve lançar TimerStateException ao tentar pausar um timer parado")
    void shouldThrowExceptionWhenPausingStoppedTimer() {
        // O timer inicia como STOPPED por padrão no construtor
        assertThrows(TimerStateException.class, () -> service.pause());
    }

    @Test
    @DisplayName("Deve resetar o estado e o tempo ao chamar o stop")
    void shouldResetStateAndTimeWhenStopped() {
        service.start();
        // Simulamos que o tempo passou um pouco (embora aqui o teste seja instantâneo)
        service.stop();
        
        assertEquals(TimerState.STOPPED, service.getTimerState());
        assertEquals(DEFAULT_TIME, service.getRemainingSeconds());
    }
}