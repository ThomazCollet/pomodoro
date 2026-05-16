package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ChallengeNotFoundException;
import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.model.ChallengeType;
import com.thomazcollet.domain.repository.ChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository repository;

    @InjectMocks
    private ChallengeService service;

    private Challenge streakChallenge;
    private Challenge milestoneChallenge;

    @BeforeEach
    void setUp() {
        // Mock de um desafio de Streak (Consistência)
        streakChallenge = new Challenge();
        streakChallenge.setId(1L);
        streakChallenge.setProfileId(10L);
        streakChallenge.setType(ChallengeType.STREAK_CHALLENGE);
        streakChallenge.setTitle("Foco Diário");
        streakChallenge.setDurationDays(5);
        streakChallenge.setMinFocusMinutesPerDay(30);
        streakChallenge.setLivesTotal(2);

        // Mock de um desafio de Milestone (Acumulado)
        milestoneChallenge = new Challenge();
        milestoneChallenge.setId(2L);
        milestoneChallenge.setProfileId(10L);
        milestoneChallenge.setType(ChallengeType.MILESTONE_CHALLENGE);
        milestoneChallenge.setTitle("Maratona Java");
        milestoneChallenge.setTargetTotalMinutes(120); // 2 horas de foco total
        milestoneChallenge.setDurationDays(30);
    }

    // --- TESTES DE CRIAÇÃO E VALIDAÇÃO ---

    @Test
    @DisplayName("Deve lançar exceção ao criar Milestone sem meta de minutos")
    void shouldThrowExceptionForMilestoneWithoutTarget() {
        milestoneChallenge.setTargetTotalMinutes(0);
        assertThrows(IllegalArgumentException.class, () -> service.createChallenge(milestoneChallenge));
    }

    // --- TESTES DE PROGRESSO EM TEMPO REAL (ADD MINUTES) ---

    @Test
    @DisplayName("Deve atualizar apenas foco diário para desafios de Streak")
    void shouldUpdateOnlyDailyFocusForStreak() {
        when(repository.findById(1L)).thenReturn(Optional.of(streakChallenge));
        streakChallenge.setStatus(ChallengeStatus.ACTIVE);
        streakChallenge.setTodayFocusMinutes(10);

        service.addFocusMinutes(1L, 25);

        // Deve somar 10 + 25 = 35
        verify(repository).updateDailyFocus(1L, 35);
        // NÃO deve chamar updateMilestoneProgress
        verify(repository, never()).updateMilestoneProgress(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Deve atualizar acumulado e completar Milestone quando meta é atingida")
    void shouldCompleteMilestoneWhenTargetReached() {
        when(repository.findById(2L)).thenReturn(Optional.of(milestoneChallenge));
        milestoneChallenge.setStatus(ChallengeStatus.ACTIVE);
        milestoneChallenge.setAccumulatedMinutes(100);

        // Faltam 20 para 120. Adicionamos 25.
        service.addFocusMinutes(2L, 25);

        verify(repository).updateMilestoneProgress(2L, 125, "COMPLETED");
    }

    @Test
    @DisplayName("Deve propagar minutos para todos os desafios ativos do perfil")
    void shouldPropagateMinutesToAllActiveChallenges() {
        // CONFIGURAÇÃO: Garantir que ambos estão ATIVOS para passar pelo IF do service
        streakChallenge.setStatus(ChallengeStatus.ACTIVE);
        milestoneChallenge.setStatus(ChallengeStatus.ACTIVE);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(streakChallenge, milestoneChallenge));

        when(repository.findById(1L)).thenReturn(Optional.of(streakChallenge));
        when(repository.findById(2L)).thenReturn(Optional.of(milestoneChallenge));

        // EXECUÇÃO
        service.addFocusMinutesToActiveChallenges(10L, 25);

        // VERIFICAÇÃO
        verify(repository).updateDailyFocus(1L, 25);
        verify(repository).updateDailyFocus(2L, 25);
        // Verifica se o Milestone também chamou o update de acumulado
        verify(repository).updateMilestoneProgress(eq(2L), anyInt(), anyString());
    }

    // --- TESTES DE PROCESSAMENTO DIÁRIO ---

    @Test
    @DisplayName("Deve perder vida no Streak se meta diária não for atingida")
    void shouldDecrementLivesWhenGoalIsNotReached() {
        streakChallenge.setLivesRemaining(2);
        streakChallenge.setStatus(ChallengeStatus.ACTIVE);
        // CONFIGURAÇÃO FUNDAMENTAL: O service agora lê o foco de dentro do objeto
        streakChallenge.setTodayFocusMinutes(10); // Meta era 30

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(streakChallenge));

        service.processDailyProgress(10L, 0); // O segundo parâmetro agora é indiferente para a regra

        verify(repository).updateProgress(1L, 0, 1, "ACTIVE");
        verify(repository).updateDailyFocus(1L, 0);
    }

    @Test
    @DisplayName("Deve completar Streak no último dia com sucesso")
    void shouldCompleteStreakOnLastDay() {
        streakChallenge.setProgressDays(4); // Faltava 1 dia (total 5)
        streakChallenge.setLivesRemaining(2);
        streakChallenge.setStatus(ChallengeStatus.ACTIVE);
        // CONFIGURAÇÃO FUNDAMENTAL: Simula que o usuário bateu a meta neste desafio
        // específico
        streakChallenge.setTodayFocusMinutes(30);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(streakChallenge));

        service.processDailyProgress(10L, 0);

        verify(repository).updateProgress(1L, 5, 2, "COMPLETED");
    }

    @Test
    @DisplayName("Deve concluir Milestone no fechamento do dia se o progresso acumulado atingiu a meta total")
    void shouldCompleteMilestoneOnDailyProgressIfTargetIsMet() {
        milestoneChallenge.setStatus(ChallengeStatus.ACTIVE);
        milestoneChallenge.setAccumulatedMinutes(120); // Atingiu o target total de 120
        milestoneChallenge.setTodayFocusMinutes(20);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(milestoneChallenge));

        service.processDailyProgress(10L, 0);

        // Verifica se a malha de segurança capturou o status COMPLETED
        verify(repository).updateMilestoneProgress(2L, 120, "COMPLETED");
        verify(repository).updateDailyFocus(2L, 0);
    }
}