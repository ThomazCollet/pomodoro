package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ChallengeNotFoundException;
import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.model.ChallengeType;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.ChallengeRepository;
import com.thomazcollet.domain.repository.ProfileRepository;
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

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ChallengeService service;

    private Challenge streakChallenge;
    private Challenge milestoneChallenge;
    private Profile fakeProfile;

    @BeforeEach
    void setUp() {
        // Mock do Perfil do Usuário
        fakeProfile = new Profile();
        fakeProfile.setId(10L);
        fakeProfile.setXp(100);

        // Mock de um desafio de Streak (Consistência) - 5 dias
        streakChallenge = new Challenge();
        streakChallenge.setId(1L);
        streakChallenge.setProfileId(10L);
        streakChallenge.setType(ChallengeType.STREAK_CHALLENGE);
        streakChallenge.setTitle("Foco Diário");
        streakChallenge.setDurationDays(5);
        streakChallenge.setMinFocusMinutesPerDay(30);
        streakChallenge.setLivesTotal(2);

        // Mock de um desafio de Milestone (Acumulado) - 120 minutos
        milestoneChallenge = new Challenge();
        milestoneChallenge.setId(2L);
        milestoneChallenge.setProfileId(10L);
        milestoneChallenge.setType(ChallengeType.MILESTONE_CHALLENGE);
        milestoneChallenge.setTitle("Maratona Java");
        milestoneChallenge.setTargetTotalMinutes(120);
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

        verify(repository).updateDailyFocus(1L, 35);
        verify(repository, never()).updateMilestoneProgress(anyLong(), anyInt(), anyString());
        verify(profileRepository, never()).updateXp(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Deve atualizar acumulado, completar Milestone e conceder XP proporcional (Meta / 10)")
    void shouldCompleteMilestoneWhenTargetReached() {
        when(repository.findById(2L)).thenReturn(Optional.of(milestoneChallenge));
        when(profileRepository.findById(10L)).thenReturn(Optional.of(fakeProfile));

        milestoneChallenge.setStatus(ChallengeStatus.ACTIVE);
        milestoneChallenge.setAccumulatedMinutes(100);

        // Faltam 20 para 120. Adicionamos 25. Meta batida!
        service.addFocusMinutes(2L, 25);

        verify(repository).updateMilestoneProgress(2L, 125, "COMPLETED");

        // MATRIZ NOVA: Meta de 120 minutos / 10 = 12 XP.
        // 100 XP (Antigo) + 12 XP (Novo) = 112 XP
        verify(profileRepository).updateXp(10L, 112);
    }

    @Test
    @DisplayName("Deve propagar minutos para todos os desafios ativos do perfil")
    void shouldPropagateMinutesToAllActiveChallenges() {
        streakChallenge.setStatus(ChallengeStatus.ACTIVE);
        milestoneChallenge.setStatus(ChallengeStatus.ACTIVE);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(streakChallenge, milestoneChallenge));
        when(repository.findById(1L)).thenReturn(Optional.of(streakChallenge));
        when(repository.findById(2L)).thenReturn(Optional.of(milestoneChallenge));

        service.addFocusMinutesToActiveChallenges(10L, 25);

        verify(repository).updateDailyFocus(1L, 25);
        verify(repository).updateDailyFocus(2L, 25);
        verify(repository).updateMilestoneProgress(eq(2L), anyInt(), anyString());
    }

    // --- TESTES DE PROCESSAMENTO DIÁRIO ---

    @Test
    @DisplayName("Deve perder vida no Streak se meta diária não for atingida")
    void shouldDecrementLivesWhenGoalIsNotReached() {
        streakChallenge.setLivesRemaining(2);
        streakChallenge.setStatus(ChallengeStatus.ACTIVE);
        streakChallenge.setTodayFocusMinutes(10);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(streakChallenge));

        service.processDailyProgress(10L, 0);

        verify(repository).updateProgress(1L, 0, 1, "ACTIVE");
        verify(repository).updateDailyFocus(1L, 0);
        verify(profileRepository, never()).updateXp(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Deve completar Streak no último dia com sucesso e conceder XP proporcional (Dias * 10)")
    void shouldCompleteStreakOnLastDay() {
        streakChallenge.setProgressDays(4);
        streakChallenge.setLivesRemaining(2);
        streakChallenge.setStatus(ChallengeStatus.ACTIVE);
        streakChallenge.setTodayFocusMinutes(30);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(streakChallenge));
        when(profileRepository.findById(10L)).thenReturn(Optional.of(fakeProfile));

        service.processDailyProgress(10L, 0);

        verify(repository).updateProgress(1L, 5, 2, "COMPLETED");

        // MATRIZ NOVA: 5 dias de duração x 10 XP = 50 XP.
        // 100 XP (Antigo) + 50 XP (Novo) = 150 XP
        verify(profileRepository).updateXp(10L, 150);
    }

    @Test
    @DisplayName("Deve concluir Milestone no fechamento do dia e entregar XP se o progresso acumulado atingiu a meta")
    void shouldCompleteMilestoneOnDailyProgressIfTargetIsMet() {
        milestoneChallenge.setStatus(ChallengeStatus.ACTIVE);
        milestoneChallenge.setAccumulatedMinutes(120);
        milestoneChallenge.setTodayFocusMinutes(20);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(milestoneChallenge));
        when(profileRepository.findById(10L)).thenReturn(Optional.of(fakeProfile));

        service.processDailyProgress(10L, 0);

        verify(repository).updateMilestoneProgress(2L, 120, "COMPLETED");
        verify(repository).updateDailyFocus(2L, 0);

        // MATRIZ NOVA: Meta de 120 minutos / 10 = 12 XP.
        // 100 XP (Antigo) + 12 XP (Novo) = 112 XP
        verify(profileRepository).updateXp(10L, 112);
    }
}